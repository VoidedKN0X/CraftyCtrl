package com.vknxworks.craftyremote.data

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
    private val token: String?,
    private val onUnauthorized: () -> Unit = {},
) {
    val baseUrl: String = normalizeUrl(baseUrl)

    suspend fun servers(): List<ServerSummary> =
        call("GET", "/servers").dataArray()
            .mapNotNull { (it as? JsonObject)?.let(ServerSummary::from) }

    /** Creates a server (see CreateServerScreen for the body). Returns the new server's id. */
    suspend fun createServer(body: JsonObject): String {
        val res = call("POST", "/servers", jsonBody(body)) as? JsonObject
        return res?.obj("data")?.let { it.str("new_server_id") ?: it.str("new_server_uuid") }
            ?: throw CraftyException(0, "NO_ID", "Crafty created the server but didn't say which one it is. Pull to refresh the list.")
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
        throw CraftyException(code, "HOST_STATS", "Host stats need an account with full panel access.")
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
        val chunkSize = UPLOAD_CHUNK
        val total = maxOf(1L, (size + chunkSize - 1) / chunkSize).toInt()
        val url = "$baseUrl/api/v2/servers/$serverId/files/upload/".toHttpUrlOrNull()
            ?: throw CraftyException(0, "BAD_URL", "That panel address isn't a valid URL.")

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

    /** Console command, without the leading slash. Crafty expects a plain-text body. */
    suspend fun sendCommand(serverId: String, command: String) {
        call("POST", "/servers/$serverId/stdin", command.toRequestBody(TEXT))
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
            "This Crafty version can't list schedules through its API (${e.message})",
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
                    throw CraftyException(401, "HTTP_401", "Not signed in, or the session expired.")
                }
            }
            start += TASK_PROBE_BATCH
        }
        if (lookupsWork) found.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE } else null
    }

    /** Status code and body of an authenticated GET; code 0 means a network failure. */
    private suspend fun getRaw(path: String, apiPrefix: Boolean = true): Pair<Int, String> = withContext(Dispatchers.IO) {
        val prefix = if (apiPrefix) "/api/v2" else ""
        val url = "$baseUrl$prefix$path".toHttpUrlOrNull() ?: return@withContext 0 to "Invalid URL"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        try {
            http.newCall(request).execute().use { resp -> resp.code to resp.body?.string().orEmpty() }
        } catch (e: IOException) {
            0 to "Network error: ${networkMessage(e)}"
        }
    }

    /** Status code and body of a request, never throwing. Used by the diagnostics view. */
    suspend fun raw(path: String, postJson: String? = null): String {
        val (code, body) = if (postJson == null) getRaw(path) else postRaw(path, postJson)
        if (code == 0) return body
        val shown = if (body.length > RAW_LIMIT) body.take(RAW_LIMIT) + "\n…(${body.length} chars total)" else body
        return "HTTP $code\n$shown"
    }

    private suspend fun postRaw(path: String, json: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v2$path".toHttpUrlOrNull() ?: return@withContext 0 to "Invalid URL"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .post(json.toRequestBody(JSON_TYPE))
            .build()
        try {
            http.newCall(request).execute().use { resp -> resp.code to resp.body?.string().orEmpty() }
        } catch (e: IOException) {
            0 to "Network error: ${networkMessage(e)}"
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

    private suspend fun call(
        method: String,
        path: String,
        body: RequestBody? = null,
    ): JsonElement = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v2$path".toHttpUrlOrNull()
            ?: throw CraftyException(0, "BAD_URL", "That panel address isn't a valid URL.")

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
                "Got a response that isn't from Crafty. Check the panel address.",
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
        private val main = Handler(Looper.getMainLooper())
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

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

        /** Username/password (+ optional TOTP) login. Returns a bearer token. */
        suspend fun login(baseUrl: String, username: String, password: String, totp: String?): String {
            val body = buildJsonObject {
                put("username", username)
                put("password", password)
                if (!totp.isNullOrBlank()) put("totp", totp)
            }
            val res = CraftyApi(baseUrl, null).call("POST", "/auth/login", jsonBody(body))
            return (res as? JsonObject)?.obj("data")?.str("token")
                ?: throw CraftyException(0, "NO_TOKEN", "Crafty didn't return a token.")
        }

        private fun describeError(code: Int, err: String, data: String?): String {
            val upper = err.uppercase()
            val base = when {
                "TOTP" in upper || "MFA" in upper || "2FA" in upper -> "A valid 2FA code is required."
                "CRED" in upper || "PASSWORD" in upper || "INCORRECT" in upper -> "Wrong username or password."
                code == 401 -> "Not signed in, or the session expired."
                code == 403 || "NOT_AUTHORIZED" in upper -> "Crafty refused this — the key or user lacks permission."
                code == 404 -> "Not found. This Crafty version may not support it."
                code == 429 -> "Too many attempts. Wait a bit and try again."
                code >= 500 -> "Crafty hit an internal error."
                else -> err.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            }
            return if (!data.isNullOrBlank() && data != err) "$base ($data)" else base
        }

        private fun networkMessage(e: IOException): String = when (e) {
            is UnknownHostException -> "Can't find that host. Check the address and your connection."
            is SSLException -> "Secure connection failed — Android doesn't trust the panel's certificate."
            is SocketTimeoutException -> "Crafty took too long to respond."
            is ConnectException -> "Couldn't connect. Is Crafty reachable from this network?"
            else -> e.message?.takeIf { it.isNotBlank() } ?: "Network error."
        }
    }
}
