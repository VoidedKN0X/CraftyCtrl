package com.vknxworks.craftyctrl.data

import android.text.Html
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Crafty 4's live console: the same WebSocket the web panel's terminal uses. Crafty only pushes a server's
 * console lines (event "vterm_new_line") to sockets that say they are showing that server's page, and it
 * checks the login from the "token" cookie, which takes the same token as the REST API.
 *
 * Only reads the console. Commands still go through the REST API's stdin call, as in the web panel.
 */
class ConsoleSocket(
    baseUrl: String,
    private val serverId: String,
    private val token: String,
    private val listener: Listener,
) {
    interface Listener {
        fun onOpen()
        fun onLine(line: String)
        /** The socket closed or failed; [unauthorized] when Crafty refused the login. */
        fun onClosed(unauthorized: Boolean)
    }

    private val base = CraftyApi.normalizeUrl(baseUrl)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var closedByUs = false

    fun connect(): Boolean {
        val http = base.toHttpUrlOrNull() ?: return false
        // Crafty's panel connects to <panel>/ws?page=<page path>&page_query_params=<page query>.
        val url = http.newBuilder()
            .addPathSegment("ws")
            .addQueryParameter("page", "/panel/server_detail")
            .addQueryParameter("page_query_params", "?id=$serverId")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Cookie", "token=$token")
            .header("Authorization", "Bearer $token")
            // Tornado refuses WebSockets whose Origin isn't the panel itself.
            .header("Origin", "${http.scheme}://${http.host}" + if (http.port != okhttp3.HttpUrl.defaultPort(http.scheme)) ":${http.port}" else "")
            .build()
        closedByUs = false
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()

            override fun onMessage(webSocket: WebSocket, text: String) {
                val o = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
                val event = (o["event"] as? JsonPrimitive)?.contentOrNull
                when (event) {
                    "vterm_new_line" -> {
                        val data = o["data"]
                        val raw = (data as? JsonObject)?.get("line")?.let { (it as? JsonPrimitive)?.contentOrNull }
                            ?: (data as? JsonPrimitive)?.contentOrNull
                            ?: return
                        // Crafty sends HTML: escaped text with highlight spans, lines ending in <br />.
                        raw.split(lineBreak).forEach { part ->
                            val line = clean(part)
                            if (line.isNotBlank()) listener.onLine(line)
                        }
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closedByUs) listener.onClosed(code == UNAUTHORIZED || code == 4001 || code == 1008)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closedByUs) listener.onClosed(response?.code == 401 || response?.code == 403)
            }
        })
        return true
    }

    fun close() {
        closedByUs = true
        socket?.close(1000, null)
        socket = null
    }

    companion object {
        /** Crafty's close code for a missing or rejected login. */
        private const val UNAUTHORIZED = 4013
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val lineBreak = Regex("(?i)<br\\s*/?>|\\r?\\n")
        private val ansi = Regex("\u001B\\[[;?0-9]*[A-Za-z]")

        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()

        /** HTML to plain text: tags removed, entities decoded, colour codes dropped. */
        internal fun clean(html: String): String =
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().replace(ansi, "").trimEnd()
    }
}
