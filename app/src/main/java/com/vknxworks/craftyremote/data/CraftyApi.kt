package com.vknxworks.craftyremote.data

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
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
        call("GET", "/servers/$serverId/backups").dataItems().map(BackupConfig::from)

    suspend fun tasks(serverId: String): List<ScheduledTask> =
        call("GET", "/servers/$serverId/tasks").dataItems().mapNotNull(ScheduledTask::from)

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
