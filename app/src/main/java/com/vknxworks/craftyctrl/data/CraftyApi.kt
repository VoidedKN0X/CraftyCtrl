package com.vknxworks.craftyctrl.data

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str

class CraftyException(
    val httpCode: Int,
    val errorCode: String,
    message: String,
) : Exception(message)

/**
 * Thin client for Crafty Controller 4's REST API (v2).
 * Uses Android's normal certificate checks — the panel must have a valid HTTPS certificate.
 */
class CraftyApi(
    baseUrl: String,
    private val fixedToken: String?,
    private val onUnauthorized: () -> Unit = {},
    /** Where the app's screens get the token from, so it can be wiped while the app is locked. */
    private val tokenProvider: (() -> String?)? = null,
) {
    val baseUrl: String = normalizeUrl(baseUrl)

    /**
     * The token for the next request. With a [tokenProvider] and no token (the app is locked), nothing
     * is sent at all, and it doesn't count as being signed out.
     */
    private val token: String?
        get() {
            if (tokenProvider == null) return fixedToken
            return tokenProvider.invoke()
                ?: throw CraftyException(0, "LOCKED", str(R.string.craftyctrl_is_locked))
        }

    suspend fun servers(): List<ServerSummary> =
        call("GET", "/servers").dataArray()
            .mapNotNull { (it as? JsonObject)?.let(ServerSummary::from) }

    /** Creates a server (see CreateServerScreen for the body). Returns the new server's id. */
    suspend fun createServer(body: JsonObject): String {
        val res = call("POST", "/servers", jsonBody(body)) as? JsonObject
        return res?.obj("data")?.let { it.str("new_server_id") ?: it.str("new_server_uuid") }
            ?: throw CraftyException(0, "NO_ID", str(R.string.crafty_created_the_server_but_didn))
    }

    /**
     * Removes a server from Crafty. With [deleteFiles], Crafty also deletes its folder
     * (world, configs, jar); otherwise the files stay on disk and only the server entry goes.
     */
    suspend fun deleteServer(serverId: String, deleteFiles: Boolean) {
        call("DELETE", "/servers/$serverId" + if (deleteFiles) "?files=true" else "")
    }

    suspend fun serverInfo(serverId: String): ServerSummary? =
        (call("GET", "/servers/$serverId") as? JsonObject)?.obj("data")?.let(ServerSummary::from)

    /**
     * Version when the stats ping doesn't report one: first Crafty's Prometheus metrics
     * (Minecraft_Version_info{version="Paper 1.21.10"}), then the jar's file name.
     */
    suspend fun versionFallback(serverId: String, executable: String?): String? {
        val (code, body) = getRaw("/metrics/servers/$serverId", apiPrefix = false)
        if (code == 200) {
            Regex("""Minecraft_Version_info\{[^}]*version="([^"]+)"""").find(body)
                ?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals("false", true) && !it.equals("none", true) }
                ?.let { return it }
        }
        // The startup line in the console log: Java "Starting minecraft server version 26.3",
        // Bedrock "Version: 1.21.x". Only present while it's still in Crafty's console buffer.
        runCatching { logs(serverId) }.getOrNull()?.let { lines ->
            val re = Regex("""(?i)\bversion:?\s+v?(\d+\.\d+(?:\.\d+)*)""")
            lines.asReversed().firstNotNullOfOrNull { re.find(it)?.groupValues?.get(1) }?.let { return it }
        }
        return versionFromJar(executable)
    }

    suspend fun history(serverId: String): List<HistoryPoint> =
        parseHistory(call("GET", "/servers/$serverId/history"))

    /** Host CPU/RAM/disk. Prefers /crafty/stats, falls back to the Prometheus endpoint. */
    suspend fun hostStats(): HostStats {
        val first = try {
            HostStats.fromJson(call("GET", "/crafty/stats"))
        } catch (e: CraftyException) {
            if (e.httpCode == 401) throw e
            null
        }
        if (first != null && !first.isEmpty) return first
        val (code, body) = getRaw("/metrics/host", apiPrefix = false)
        if (code == 200) {
            val m = HostStats.fromMetrics(body)
            if (!m.isEmpty) return m
        }
        throw CraftyException(code, "HOST_STATS", str(R.string.host_stats_need_an_account_with))
    }

    /**
     * Lists a folder or reads a file. Crafty uses one POST for both; some versions require a
     * "page" field and some reject unknown fields, so try with it first and retry without.
     */
    suspend fun browse(serverId: String, path: String): FileResult {
        val withPage = buildJsonObject { put("page", "files"); put("path", path) }
        val root = try {
            call("POST", "/servers/$serverId/files", jsonBody(withPage))
        } catch (e: CraftyException) {
            if (e.httpCode != 400) throw e
            call("POST", "/servers/$serverId/files", jsonBody(buildJsonObject { put("path", path) }))
        }
        return parseFileResult(root, path)
    }

    /**
     * Saves a text file. Crafty answers 409 (conflict) unless it's told to overwrite an
     * existing file, so the user's explicit Save always overwrites.
     */
    suspend fun saveFile(serverId: String, path: String, contents: String) {
        val body = buildJsonObject {
            put("path", path)
            put("contents", contents)
            put("overwrite", true)
        }
        call("PATCH", "/servers/$serverId/files", jsonBody(body))
    }

    /**
     * Renames or moves an item. Crafty joins [newName] onto the item's current folder, so a move
     * passes a path relative to that folder (e.g. "../plugins/config.yml").
     */
    suspend fun renameEntry(serverId: String, path: String, newName: String) {
        val body = buildJsonObject {
            put("path", path)
            put("new_name", newName)
        }
        call("PATCH", "/servers/$serverId/files/create", jsonBody(body))
    }

    /** Deletes a file or a folder with everything in it. */
    suspend fun deleteEntry(serverId: String, path: String) {
        val current = buildJsonObject {
            put("file_system_objects", kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject { put("filename", path) })
            })
        }
        try {
            call("DELETE", "/servers/$serverId/files", jsonBody(current))
        } catch (e: CraftyException) {
            // Older Crafty versions take a single filename instead of a list.
            if (e.httpCode != 400) throw e
            call("DELETE", "/servers/$serverId/files", jsonBody(buildJsonObject { put("filename", path) }))
        }
    }

    /**
     * Whether a Java server's eula.txt says eula=true. A missing file counts as not accepted;
     * null means the app couldn't tell (e.g. no file permission), so callers shouldn't block on it.
     */
    suspend fun eulaAccepted(serverId: String, serverPath: String): Boolean? = try {
        when (val r = browse(serverId, join(serverPath, "eula.txt"))) {
            // Mirror Crafty's own check: it only reads the first line and wants exactly "eula=true".
            // (Minecraft accepts comment lines above it, but then Crafty refuses to start the server.)
            is FileResult.Text -> r.contents.lineSequence().firstOrNull()?.trim()?.lowercase() == "eula=true"
            is FileResult.Folder -> null
        }
    } catch (e: CraftyException) {
        if (e.httpCode in listOf(401, 403)) null else false
    }

    /** Writes eula=true, which is what accepting the Minecraft EULA means for a Java server. */
    suspend fun acceptEula(serverId: String, serverPath: String) {
        runCatching { createEntry(serverId, serverPath, "eula.txt", false) } // may already exist
        // Exactly what Crafty's web panel writes when you accept the EULA there.
        saveFile(serverId, join(serverPath, "eula.txt"), "eula=true")
    }

    /** Creates an empty file or folder named [name] inside [parent]. */
    suspend fun createEntry(serverId: String, parent: String, name: String, directory: Boolean) {
        val body = buildJsonObject {
            put("parent", parent)
            put("name", name)
            put("directory", directory)
        }
        call("PUT", "/servers/$serverId/files/create", jsonBody(body))
    }

    /**
     * Uploads a file into [folder] using Crafty's chunked upload (the same one its web panel uses):
     * every chunk is posted with a client-chosen upload id, its index and its SHA-256.
     * [readInto] works like InputStream.read(buffer, offset, length): bytes read, or -1 at the end.
     */
    suspend fun upload(
        serverId: String,
        folder: String,
        fileName: String,
        size: Long,
        readInto: (ByteArray, Int, Int) -> Int,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        // The name comes from whichever app provided the file; keep only its last part so a
        // name like "../plugins/x.jar" can't place the upload outside the chosen folder.
        val fileName = safeFileName(fileName)
        val chunkSize = UPLOAD_CHUNK
        val total = maxOf(1L, (size + chunkSize - 1) / chunkSize).toInt()
        val url = "$baseUrl/api/v2/servers/$serverId/files/upload/".toHttpUrlOrNull()
            ?: throw CraftyException(0, "BAD_URL", str(R.string.that_panel_address_isn_t_a))

        fun request(extra: Map<String, String>, body: ByteArray): Pair<Int, String> {
            val b = Request.Builder().url(url).header("Accept", "application/json")
            token?.let { b.header("Authorization", "Bearer $it") }
            mapOf(
                "chunked" to "true",
                "type" to "server_upload",
                "fileName" to fileName,
                "fileSize" to size.toString(),
                "chunkSize" to chunkSize.toString(),
                "totalChunks" to total.toString(),
                "location" to folder,
            ).plus(extra).forEach { (k, v) -> b.header(k, v) }
            b.post(body.toRequestBody(OCTET))
            return try {
                http.newCall(b.build()).execute().use { it.code to it.body?.string().orEmpty() }
            } catch (e: IOException) {
                throw CraftyException(0, "NETWORK", networkMessage(e))
            }
        }

        fun check(code: Int, text: String) {
            val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            if (code !in 200..299 || obj?.str("status") == "error") {
                val err = obj?.str("error") ?: "HTTP_$code"
                // Crafty answers crashes with a Python traceback; its last line names the actual error.
                val detail = obj?.str("error_data")
                    ?: text.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }?.take(160)
                throw CraftyException(code, err, describeError(code, err, detail))
            }
        }

        // Opening request: newer Crafty hands back an id for the chunks that follow.
        // Crafty expects the client to pick the upload id and send it with every chunk.
        val fileId = java.util.UUID.randomUUID().toString()

        val buffer = ByteArray(chunkSize)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (i in 0 until total) {
            // Fill a whole chunk; streams may return fewer bytes per read than asked.
            var filled = 0
            while (filled < chunkSize) {
                val n = readInto(buffer, filled, chunkSize - filled)
                if (n < 0) break
                filled += n
            }
            val chunk = buffer.copyOf(filled)
            val hash = digest.digest(chunk).joinToString("") { "%02x".format(it) }
            val extra = buildMap {
                put("chunkId", i.toString())
                put("chunkHash", hash)
                put("fileId", fileId)
            }
            val (code, text) = request(extra, chunk)
            check(code, text)
            onProgress((i + 1f) / total)
        }
    }

    suspend fun stats(serverId: String): ServerStats =
        ServerStats.from(call("GET", "/servers/$serverId/stats"))

    suspend fun logs(serverId: String): List<String> =
        call("GET", "/servers/$serverId/logs").dataArray()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .map { it.replace(ansi, "") }

    /**
     * Console command, without the leading slash. Crafty expects a plain-text body and writes it to
     * the server's console followed by a newline, so line breaks are flattened: a player name or
     * message taken from the server can never smuggle in a second command.
     */
    suspend fun sendCommand(serverId: String, command: String) {
        val oneLine = command.replace(lineBreaks, " ").replace(controlChars, " ")
        call("POST", "/servers/$serverId/stdin", oneLine.toRequestBody(TEXT))
    }

    /** start_server, stop_server, restart_server, kill_server, backup_server, ... */
    suspend fun action(serverId: String, action: String, actionId: String? = null) {
        val suffix = actionId?.let { "/$it" }.orEmpty()
        call("POST", "/servers/$serverId/action/$action$suffix")
    }

    suspend fun backups(serverId: String): List<BackupConfig> =
        call("GET", "/servers/$serverId/backups").backupItems().map(BackupConfig::from)

    /**
     * Some Crafty versions have no working "list schedules" call (it errors or isn't routed),
     * but single-task lookups work. In that case, look task ids up one by one instead.
     */
    suspend fun tasks(serverId: String): List<ScheduledTask> = try {
        call("GET", "/servers/$serverId/tasks").dataItems().mapNotNull(ScheduledTask::from)
    } catch (e: CraftyException) {
        if (e.httpCode in listOf(401, 403) || e.httpCode == 0) throw e
        probeTasks(serverId) ?: throw CraftyException(
            e.httpCode, e.errorCode,
            str(R.string.this_crafty_version_can_t_list, e.message),
        )
    }

    /**
     * Looks task ids up one by one. Crafty answers a missing id with a 500 whose body names
     * "SchedulesDoesNotExist", which still proves single lookups work. Returns null only if
     * lookups fail for some other reason, so the caller can report the original error.
     */
    private suspend fun probeTasks(serverId: String): List<ScheduledTask>? = coroutineScope {
        val found = mutableListOf<ScheduledTask>()
        var lookupsWork = false
        var start = 1
        var highestHit = 0
        while (start <= MAX_TASK_PROBE && start <= highestHit + TASK_PROBE_GAP) {
            val batch = (start until start + TASK_PROBE_BATCH).map { id ->
                async { id to getRaw("/servers/$serverId/tasks/$id") }
            }.awaitAll()
            for ((id, result) in batch) {
                val (code, body) = result
                if (code == 200) {
                    // Crafty sends the task as a bare object here, not wrapped in "data".
                    val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: continue
                    val data = root.obj("data") ?: root
                    lookupsWork = true
                    // Task ids are shared across servers, so keep scanning past other servers' tasks.
                    highestHit = maxOf(highestHit, id)
                    val owner = data.str("server_id")
                    if (owner == null || owner == serverId) {
                        val withId = if (data.str("schedule_id") == null && data.str("id") == null) {
                            JsonObject(data + ("schedule_id" to JsonPrimitive(id.toString())))
                        } else {
                            data
                        }
                        ScheduledTask.from(withId)?.let(found::add)
                    }
                } else if ("DoesNotExist" in body) {
                    lookupsWork = true
                } else if (code == 401) {
                    main.post { onUnauthorized() }
                    throw CraftyException(401, "HTTP_401", str(R.string.not_signed_in_or_the_session))
                }
            }
            start += TASK_PROBE_BATCH
        }
        if (lookupsWork) found.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE } else null
    }

    /** Status code and body of an authenticated GET; code 0 means a network failure. */
    private suspend fun getRaw(path: String, apiPrefix: Boolean = true): Pair<Int, String> = withContext(Dispatchers.IO) {
        val prefix = if (apiPrefix) "/api/v2" else ""
        val url = "$baseUrl$prefix$path".toHttpUrlOrNull() ?: return@withContext 0 to str(R.string.invalid_url)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        try {
            http.newCall(request).execute().use { resp -> resp.code to resp.body?.string().orEmpty() }
        } catch (e: IOException) {
            0 to str(R.string.network_error_x, networkMessage(e))
        }
    }

    /** Status code and body of a request, never throwing. Used by the diagnostics view. */
    suspend fun raw(path: String, postJson: String? = null): String {
        val (code, body) = if (postJson == null) getRaw(path) else postRaw(path, postJson)
        if (code == 0) return body
        val shown = if (body.length > RAW_LIMIT) body.take(RAW_LIMIT) + str(R.string.x_chars_total, body.length) else body
        return "HTTP $code\n$shown"
    }

    private suspend fun postRaw(path: String, json: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v2$path".toHttpUrlOrNull() ?: return@withContext 0 to str(R.string.invalid_url)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .post(json.toRequestBody(JSON_TYPE))
            .build()
        try {
            http.newCall(request).execute().use { resp -> resp.code to resp.body?.string().orEmpty() }
        } catch (e: IOException) {
            0 to str(R.string.network_error_x, networkMessage(e))
        }
    }

    suspend fun createTask(serverId: String, fields: JsonObject) {
        call("POST", "/servers/$serverId/tasks", jsonBody(fields))
    }

    suspend fun updateTask(serverId: String, taskId: String, fields: JsonObject) {
        call("PATCH", "/servers/$serverId/tasks/$taskId", jsonBody(fields))
    }

    suspend fun deleteTask(serverId: String, taskId: String) {
        call("DELETE", "/servers/$serverId/tasks/$taskId")
    }

    /**
     * Saves a backup profile. Crafty 4.4+ addresses each profile by id; older versions have a
     * single per-server config, so fall back to that endpoint if the first isn't routed.
     */
    suspend fun updateBackup(serverId: String, backupId: String?, fields: JsonObject) {
        if (backupId != null) {
            try {
                call("PATCH", "/servers/$serverId/backups/backup/$backupId", jsonBody(fields))
                return
            } catch (e: CraftyException) {
                if (e.httpCode !in listOf(404, 405)) throw e
            }
        }
        call("PATCH", "/servers/$serverId/backups/config", jsonBody(fields))
    }

    suspend fun setTaskEnabled(serverId: String, taskId: String, enabled: Boolean) {
        val body = buildJsonObject { put("enabled", enabled) }
        call("PATCH", "/servers/$serverId/tasks/$taskId", jsonBody(body))
    }

    // ------------------------------------------------------------ server management

    /** Crafty's settings for the server (name, auto-start, start command, ...) and its live status flags. */
    suspend fun serverConfig(serverId: String): ServerConfig =
        ServerConfig.from(call("GET", "/servers/$serverId"))
            ?: throw CraftyException(0, "BAD_RESPONSE", str(R.string.crafty_didn_t_send_this_server))

    /** Changes Crafty's settings for the server; only the given fields change. */
    suspend fun updateServer(serverId: String, fields: JsonObject) {
        call("PATCH", "/servers/$serverId", jsonBody(fields))
    }

    /** Like [action], but returns Crafty's reply (clone_server answers with the new server's id). */
    suspend fun actionResult(serverId: String, action: String): JsonElement =
        call("POST", "/servers/$serverId/action/$action")

    /** Runs a scheduled task straight away. */
    suspend fun runTask(serverId: String, taskId: String) {
        call("POST", "/servers/$serverId/tasks/$taskId/run")
    }

    /** Extracts a .zip inside the server folder, next to the archive. */
    suspend fun unzip(serverId: String, path: String) {
        call("POST", "/servers/$serverId/files/zip", jsonBody(buildJsonObject { put("folder", path) }))
    }

    /** Contents of a text file in the server folder, or null if it doesn't exist. */
    suspend fun readText(serverId: String, path: String): String? = try {
        when (val r = browse(serverId, path)) {
            is FileResult.Text -> r.contents
            is FileResult.Folder -> null
        }
    } catch (e: CraftyException) {
        // A missing file comes back as one of several errors depending on the Crafty version.
        if (e.httpCode in listOf(0, 401, 403)) throw e else null
    }

    /** Writes a whole text file, creating it first if needed. */
    suspend fun writeText(serverId: String, folder: String, name: String, contents: String, exists: Boolean) {
        if (!exists) runCatching { createEntry(serverId, folder, name, false) }
        saveFile(serverId, join(folder, name), contents)
    }

    /**
     * Backup archives for a backup profile. Crafty has no documented call for this, so try the
     * likely ones and, last, the backup folder through the file browser. Null means none worked.
     */
    suspend fun backupFiles(serverId: String, backupId: String?, location: String?): List<String>? {
        val paths = buildList {
            if (backupId != null) {
                add("/servers/$serverId/backups/backup/$backupId/files")
                add("/servers/$serverId/backups/backup/$backupId")
            }
        }
        for (p in paths) {
            val (code, body) = getRaw(p)
            if (code != 200) continue
            val names = backupNamesIn(runCatching { json.parseToJsonElement(body) }.getOrNull())
            if (names.isNotEmpty()) return names.sortedDescending()
        }
        if (location != null && backupId != null) {
            for (folder in listOf(join(location, backupId), location)) {
                val r = runCatching { browse(serverId, folder) }.getOrNull() as? FileResult.Folder ?: continue
                val names = r.entries.filter { !it.isDir }.map { it.name }.filter { backupNamesIn(JsonPrimitive(it)).isNotEmpty() }
                if (names.isNotEmpty()) return names.sortedDescending()
            }
        }
        return null
    }

    /**
     * Restores a backup archive. [inPlace] extracts over the current files; otherwise Crafty
     * clears the server folder first. The endpoint moved between Crafty versions, so try both.
     */
    suspend fun restoreBackup(serverId: String, backupId: String?, fileName: String, inPlace: Boolean) {
        val body = buildJsonObject {
            put("filename", fileName)
            put("inPlace", inPlace)
        }
        // Crafty before 4.4 has one backup setup per server and no profile ids.
        val base = if (backupId != null) "/servers/$serverId/backups/backup/$backupId" else "/servers/$serverId/backups/backup"
        try {
            call("POST", "$base/restore", jsonBody(body))
        } catch (e: CraftyException) {
            if (e.httpCode !in listOf(404, 405)) throw e
            call("POST", base, jsonBody(body))
        }
    }

    private suspend fun call(
        method: String,
        path: String,
        body: RequestBody? = null,
    ): JsonElement = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v2$path".toHttpUrlOrNull()
            ?: throw CraftyException(0, "BAD_URL", str(R.string.that_panel_address_isn_t_a))

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .method(method, body ?: if (method == "GET" || method == "DELETE") null else ByteArray(0).toRequestBody())
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw CraftyException(0, "NETWORK", networkMessage(e))
        }

        response.use { resp ->
            val text = try {
                resp.body?.string().orEmpty()
            } catch (e: IOException) {
                throw CraftyException(0, "NETWORK", networkMessage(e))
            }
            val root = runCatching { json.parseToJsonElement(text) }.getOrNull()
            val obj = root as? JsonObject

            if (!resp.isSuccessful || obj?.str("status") == "error") {
                if (resp.code == 401) main.post { onUnauthorized() }
                val code = obj?.str("error") ?: "HTTP_${resp.code}"
                throw CraftyException(resp.code, code, describeError(resp.code, code, obj?.str("error_data")))
            }

            root ?: throw CraftyException(
                resp.code, "BAD_RESPONSE",
                str(R.string.got_a_response_that_isn_t),
            )
        }
    }

    companion object {
        /** "paper-1.21.1.jar" -> "Paper 1.21.1"; "server.jar" -> null. */
        fun versionFromJar(executable: String?): String? {
            val name = executable?.substringAfterLast('/')?.removeSuffix(".jar") ?: return null
            val m = Regex("""^([A-Za-z]+)?[-_ ]*(\d+\.\d+(?:\.\d+)?(?:[-.][\w.]+)?)""").find(name) ?: return null
            val brand = m.groupValues[1].takeIf { it.isNotEmpty() && !it.equals("server", true) && !it.equals("minecraft", true) }
            val ver = m.groupValues[2].substringBefore("-build").substringBefore("-R")
            return listOfNotNull(brand?.replaceFirstChar { it.uppercase() }, ver).joinToString(" ")
        }

        private const val MAX_TASK_PROBE = 300
        private const val TASK_PROBE_BATCH = 20
        private const val TASK_PROBE_GAP = 60
        private const val RAW_LIMIT = 4000
        private const val UPLOAD_CHUNK = 4 * 1024 * 1024
        private val OCTET = "application/octet-stream".toMediaType()
        private val TEXT = "text/plain; charset=utf-8".toMediaType()
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private val ansi = Regex("\u001B\\[[;?0-9]*[A-Za-z]")
        private val lineBreaks = Regex("[\\r\\n\\u000B\\u000C\\u001C-\\u001E\\u0085\\u2028\\u2029]+")
        /** Any other control character (escape sequences, NUL, ...) never belongs in a console command. */
        private val controlChars = Regex("[\\p{Cntrl}]")
        /** Invisible direction overrides that can make a file name read differently from what it is. */
        private val bidiControls = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
        private val main = Handler(Looper.getMainLooper())
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        /** The last part of [raw], without control characters; "upload.bin" if nothing usable is left. */
        internal fun safeFileName(raw: String): String {
            val name = raw.substringAfterLast('/').substringAfterLast('\\')
                .filter { it.code >= 32 && it.code != 127 && it != '\u0085' && it != '\u2028' && it != '\u2029' }
                .replace(bidiControls, "")
                // A colon is a drive letter or an alternate data stream on Windows hosts ("C:x", "a.txt:x").
                .replace(':', '_')
                // Windows drops trailing dots and spaces, which can turn a name like ".. ." into "..".
                .trimEnd('.', ' ')
                .trim()
                .take(200)
            return if (name.isEmpty() || name.all { it == '.' || it == ' ' }) "upload.bin" else name
        }

        private fun jsonBody(o: JsonObject): RequestBody = o.toString().toRequestBody(JSON_TYPE)

        /** Accepts "crafty.example.com", "https://host/panel/dashboard", etc. */
        fun normalizeUrl(input: String): String {
            var u = input.trim()
            if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "https://$u"
            val scheme = u.substringBefore("://")
            val rest = u.substringAfter("://")
            val host = rest.substringBefore('/')
            // Drop any pasted page path (e.g. /panel/dashboard) but keep a reverse-proxy subpath.
            val path = rest.removePrefix(host).substringBefore("/panel/").substringBefore("/login")
            return "$scheme://$host$path".trimEnd('/')
        }

        /**
         * A Java account's UUID from Mojang, dashed. Null when no account has that name;
         * throws when Mojang can't be reached.
         */
        suspend fun mojangUuid(name: String): String? = withContext(Dispatchers.IO) {
            val url = "https://api.mojang.com/users/profiles/minecraft/".toHttpUrlOrNull()!!
                .newBuilder().addPathSegment(name).build()
            val req = Request.Builder().url(url).header("Accept", "application/json").build()
            try {
                http.newCall(req).execute().use { resp ->
                    when {
                        resp.code == 200 -> (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                            ?.str("id")?.let(::dashedUuid)
                        resp.code == 204 || resp.code == 404 -> null
                        else -> throw CraftyException(resp.code, "MOJANG", str(R.string.mojang_s_name_lookup_failed_http, resp.code))
                    }
                }
            } catch (e: IOException) {
                throw CraftyException(0, "NETWORK", str(R.string.couldn_t_reach_mojang_to_look, networkMessage(e)))
            }
        }

        /** Username/password (+ optional TOTP) login. Returns a bearer token. */
        suspend fun login(baseUrl: String, username: String, password: String, totp: String?): String {
            val body = buildJsonObject {
                put("username", username)
                put("password", password)
                if (!totp.isNullOrBlank()) put("totp", totp)
            }
            val res = CraftyApi(baseUrl, null).call("POST", "/auth/login", jsonBody(body))
            return (res as? JsonObject)?.obj("data")?.str("token")
                ?: throw CraftyException(0, "NO_TOKEN", str(R.string.crafty_didn_t_return_a_token))
        }

        private fun describeError(code: Int, err: String, data: String?): String {
            val upper = err.uppercase()
            val base = when {
                "TOTP" in upper || "MFA" in upper || "2FA" in upper -> str(R.string.a_valid_2fa_code_is_required)
                "CRED" in upper || "PASSWORD" in upper || "INCORRECT" in upper -> str(R.string.wrong_username_or_password)
                code == 401 -> str(R.string.not_signed_in_or_the_session)
                code == 403 || "NOT_AUTHORIZED" in upper -> str(R.string.crafty_refused_this_the_key_or)
                code == 404 -> str(R.string.not_found_this_crafty_version_may)
                code == 429 -> str(R.string.too_many_attempts_wait_a_bit)
                code >= 500 -> str(R.string.crafty_hit_an_internal_error)
                else -> err.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            }
            return if (!data.isNullOrBlank() && data != err) "$base ($data)" else base
        }

        private fun networkMessage(e: IOException): String = when (e) {
            is UnknownHostException -> str(R.string.can_t_find_that_host_check)
            is SSLException -> str(R.string.secure_connection_failed_android_doesn_t)
            is SocketTimeoutException -> str(R.string.crafty_took_too_long_to_respond)
            is ConnectException -> str(R.string.couldn_t_connect_is_crafty_reachable)
            else -> e.message?.takeIf { it.isNotBlank() } ?: str(R.string.network_error)
        }
    }
}
