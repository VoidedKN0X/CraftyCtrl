package com.vknxworks.craftyctrl.push

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.CraftyException
import com.vknxworks.craftyctrl.monitor.CheckJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Where CraftyCtrl's registration with ntfy stands. */
sealed interface PushStatus {
    data object Off : PushStatus
    /** Push is on, but ntfy isn't installed (any more). */
    data object NoNtfy : PushStatus
    data object Registering : PushStatus
    data class Registered(val endpoint: String) : PushStatus
    /** ntfy's server is down for now; the address stays the same and ntfy says when it's back. */
    data class Unavailable(val endpoint: String) : PushStatus
    data class Failed(val reason: String) : PushStatus
}

/** What a push said happened. [event] is Crafty's event name (start_server, stop_server, ...) or a player event. */
data class PushEvent(val event: String, val serverId: String?, val detail: String?)

/**
 * Push through ntfy only: ntfy gives CraftyCtrl an endpoint URL on your ntfy server; Crafty's server webhooks
 * post to it, and ntfy hands the message to [PushReceiver]. Every push also starts a check of all servers
 * straight away, so even a message CraftyCtrl can't read works as a wake-up.
 */
object Push {
    private const val PREFS = "push"
    private const val KEY_ENDPOINT = "endpoint"

    /** The name CraftyCtrl gives its webhooks in Crafty, so setting them up again updates them instead of adding more. */
    const val WEBHOOK_NAME = "CraftyCtrl"

    /** Crafty's webhook events CraftyCtrl asks for. */
    val TRIGGERS = listOf("start_server", "stop_server", "crash_detected", "kill")
    private val BASIC_TRIGGERS = listOf("start_server", "stop_server")

    /**
     * The webhook message. Crafty renders it with Jinja and wraps it in the provider's JSON (Discord), so
     * CraftyCtrl looks for this one line anywhere in what arrives.
     */
    const val BODY_TEMPLATE = "CRAFTYCTRL|{{ event_type }}|{{ server_id }}|{{ server_name }}"

    private val marker = Regex("""CRAFTYCTRL\|([A-Za-z_]{1,40})\|([A-Za-z0-9-]{0,64})\|([^\r\n]{0,200})""")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _status = MutableStateFlow<PushStatus>(PushStatus.Off)
    val status: StateFlow<PushStatus> = _status.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun endpoint(context: Context): String? = prefs(context).getString(KEY_ENDPOINT, null)

    fun load(context: Context, enabled: Boolean) {
        val endpoint = endpoint(context)
        _status.value = when {
            !enabled -> PushStatus.Off
            !UnifiedPush.isNtfyInstalled(context) -> PushStatus.NoNtfy
            endpoint != null && UnifiedPush.unavailable.value -> PushStatus.Unavailable(endpoint)
            endpoint != null -> PushStatus.Registered(endpoint)
            _status.value is PushStatus.Failed -> _status.value
            else -> PushStatus.Registering
        }
    }

    /** At start-up: registers with ntfy again, which keeps the same address and recovers from a reinstalled ntfy. */
    fun start(context: Context) {
        val enabled = AppSettings(context).pushEnabled
        load(context, enabled)
        if (enabled && UnifiedPush.isNtfyInstalled(context)) UnifiedPush.register(context, context.getString(R.string.app_name))
    }

    /** Asks ntfy for an address. Returns false (status [PushStatus.NoNtfy]) when ntfy isn't installed. */
    fun register(context: Context): Boolean {
        if (!UnifiedPush.register(context, context.getString(R.string.app_name))) {
            _status.value = PushStatus.NoNtfy
            return false
        }
        if (_status.value !is PushStatus.Registered) _status.value = PushStatus.Registering
        return true
    }

    fun unregister(context: Context) {
        runCatching { UnifiedPush.unregister(context) }
        prefs(context).edit().remove(KEY_ENDPOINT).apply()
        _status.value = PushStatus.Off
    }

    internal fun onEndpoint(context: Context, url: String) {
        prefs(context).edit().putString(KEY_ENDPOINT, url).apply()
        UnifiedPush.onEndpoint(context)
        _status.value = PushStatus.Registered(url)
    }

    internal fun onFailed(context: Context, reason: String) {
        UnifiedPush.onLost(context, reason)
        _status.value = PushStatus.Failed(reason)
    }

    internal fun onUnregistered(context: Context) {
        UnifiedPush.onLost(context, null)
        prefs(context).edit().remove(KEY_ENDPOINT).apply()
        _status.value = PushStatus.Failed("UNREGISTERED")
    }

    internal fun onTempUnavailable(context: Context) {
        UnifiedPush.onTempUnavailable()
        endpoint(context)?.let { _status.value = PushStatus.Unavailable(it) }
    }

    internal fun onMessage() {
        UnifiedPush.onMessage()
        val st = _status.value
        if (st is PushStatus.Unavailable) _status.value = PushStatus.Registered(st.endpoint)
    }

    /**
     * Reads a push. Finds CraftyCtrl's line in any string of a JSON body (Crafty's Discord-style wrapper, for
     * example), or in a plain-text body. Anyone who knows the push address can post to it, so the event and
     * server id are checked against tight patterns, and nothing is notified without checking with Crafty first.
     */
    fun parse(content: String): PushEvent? {
        val texts = runCatching { strings(json.parseToJsonElement(content)) }.getOrNull() ?: listOf(content)
        for (t in texts) {
            val m = marker.find(t) ?: continue
            return PushEvent(
                event = m.groupValues[1].lowercase(),
                serverId = m.groupValues[2].takeIf { it.isNotEmpty() },
                detail = m.groupValues[3].trim().takeIf { it.isNotEmpty() },
            )
        }
        return null
    }

    private fun strings(e: JsonElement): List<String> = when (e) {
        is JsonPrimitive -> listOfNotNull(e.contentOrNull)
        is JsonArray -> e.flatMap(::strings)
        is JsonObject -> e.values.flatMap(::strings)
    }

    /** The webhook CraftyCtrl creates (or updates) on a server. */
    fun webhookFields(endpoint: String, triggers: List<String> = TRIGGERS) = buildJsonObject {
        put("webhook_type", "Discord")
        put("name", WEBHOOK_NAME)
        put("url", endpoint)
        put("bot_name", WEBHOOK_NAME)
        put("trigger", buildJsonArray { triggers.forEach { add(JsonPrimitive(it)) } })
        put("body", BODY_TEMPLATE)
        put("enabled", true)
    }

    /** Result of setting up the webhooks: how many servers got one, and the first error, if any. */
    data class SetupResult(val done: Int, val total: Int, val error: String?)

    /**
     * Creates CraftyCtrl's webhook on every server, or updates the one it made before (found by its name).
     * Needs an account that may edit the servers' webhooks.
     */
    suspend fun setUpWebhooks(api: CraftyApi, endpoint: String): SetupResult {
        val servers = api.servers()
        var done = 0
        var error: String? = null
        val fields = webhookFields(endpoint)
        for (s in servers) {
            try {
                val existing = runCatching { api.webhooks(s.id) }.getOrDefault(emptyList())
                    .firstOrNull { (_, o) -> (o["name"] as? JsonPrimitive)?.contentOrNull == WEBHOOK_NAME }
                val id = existing?.first
                try {
                    if (id != null) api.updateWebhook(s.id, id, fields) else api.createWebhook(s.id, fields)
                } catch (e: CraftyException) {
                    // An older Crafty that doesn't know every event: just start and stop (crashes still come from checks).
                    if (e.httpCode != 400) throw e
                    val basic = webhookFields(endpoint, BASIC_TRIGGERS)
                    if (id != null) api.updateWebhook(s.id, id, basic) else api.createWebhook(s.id, basic)
                }
                done++
            } catch (e: CraftyException) {
                if (error == null) error = e.message
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (error == null) error = e.message
            }
        }
        return SetupResult(done, servers.size, error)
    }
}

/**
 * Receives what ntfy hands over: the push address, registration errors and Crafty's webhooks. Exported because
 * ntfy is another app, so every broadcast must carry the secret token of CraftyCtrl's registration or it's
 * ignored: the token is what keeps other apps out. From Android 14 a broadcast from a sender that identifies
 * itself as an app other than ntfy is dropped too.
 */
class PushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(UnifiedPush.EXTRA_TOKEN) ?: return
        if (token != UnifiedPush.token(context)) return
        if (Build.VERSION.SDK_INT >= 34) {
            val sender = sentFromPackage
            if (sender != null && sender != UnifiedPush.NTFY) return
        }
        val app = context.applicationContext
        when (intent.action) {
            UnifiedPush.ACTION_NEW_ENDPOINT -> {
                val endpoint = intent.getStringExtra(UnifiedPush.EXTRA_ENDPOINT) ?: return
                UnifiedPush.ack(app, token, intent.getStringExtra(UnifiedPush.EXTRA_ID))
                Push.onEndpoint(app, endpoint)
            }
            UnifiedPush.ACTION_REGISTRATION_FAILED -> Push.onFailed(app, intent.getStringExtra(UnifiedPush.EXTRA_REASON) ?: "UNKNOWN")
            UnifiedPush.ACTION_UNREGISTERED -> Push.onUnregistered(app)
            UnifiedPush.ACTION_TEMP_UNAVAILABLE -> Push.onTempUnavailable(app)
            UnifiedPush.ACTION_MESSAGE -> {
                val bytes = intent.getByteArrayExtra(UnifiedPush.EXTRA_BYTES_MESSAGE)
                    ?: intent.getStringExtra(UnifiedPush.EXTRA_MESSAGE)?.toByteArray()
                UnifiedPush.ack(app, token, intent.getStringExtra(UnifiedPush.EXTRA_ID))
                Push.onMessage()
                val settings = AppSettings(app)
                if (!settings.pushEnabled || !settings.alertsEnabled) return
                val event = bytes?.let { Push.parse(it.toString(Charsets.UTF_8)) }
                // Whatever arrived, check the servers now; the event only says where to look first.
                CheckJob.runNow(app, event)
            }
        }
    }
}

/**
 * ntfy binds to this for a few seconds before it delivers a message. That lifts CraftyCtrl to foreground
 * importance, so the check can start straight away even while the phone is in deep sleep.
 */
class RaiseToForegroundService : Service() {
    private val binder = Binder()
    override fun onBind(intent: Intent?): IBinder = binder
}
