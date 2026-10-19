package com.vknxworks.craftyctrl.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vknxworks.craftyctrl.BuildConfig
import com.vknxworks.craftyctrl.ui.theme.ThemePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Opt-in, anonymous usage stats sent to the project's own self-hosted Aptabase server.
 *
 * Nothing is sent unless the user turned "Share anonymous usage stats" on, and builds without an
 * Aptabase key (anyone building from source) can't send anything at all.
 *
 * What an event contains: its name, a few fixed-value properties chosen in code (a screen's name,
 * "start" or "stop", "api_key" or "password"), the app version, the Android version, the phone model,
 * the system language and a random session ID that changes every launch or after an hour of inactivity.
 * Never sent: the panel address, usernames, server or player names, console text, file names, or any
 * persistent device or install ID. (Like any web request, the stats server sees the phone's IP address;
 * Aptabase uses it for a country lookup and doesn't store it.)
 */
object Telemetry {
    /** Whether this build can send stats at all (it has a key and an HTTPS server address). */
    val available: Boolean = BuildConfig.APTABASE_KEY.startsWith("A-SH-") && BuildConfig.APTABASE_HOST.startsWith("https://")

    /** The stored setting. Read synchronously from SharedPreferences in [init], so nothing goes out before it's known. */
    var optedIn by mutableStateOf(false)
        private set

    /** Whether the one-time question has been answered. True until [init] has read the setting. */
    var asked by mutableStateOf(true)
        private set

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // A separate client from the Crafty one: no auth headers, no shared connections, no redirects.
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private val systemProps: JSONObject by lazy {
        JSONObject()
            .put("isDebug", BuildConfig.DEBUG)
            .put("osName", "Android")
            .put("osVersion", Build.VERSION.RELEASE ?: "")
            .put("locale", Locale.getDefault().language)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("appBuildNumber", BuildConfig.VERSION_CODE.toString())
            .put("sdkVersion", "craftyctrl@${BuildConfig.VERSION_NAME}")
            .put("deviceModel", Build.MODEL ?: "")
    }

    private val queue = Channel<JSONObject>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile private var sessionId = newSessionId()
    @Volatile private var lastEventAt = 0L
    @Volatile private var started = false

    /** Reads the stored setting. Called from MainActivity.onCreate before anything is tracked. */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val s = AppSettings(app)
        optedIn = s.usageStats
        asked = s.usageStatsAsked
        if (available && !started) {
            started = true
            // One event at a time, in order. Failures are dropped: stats are never stored or retried.
            scope.launch { for (event in queue) send(event) }
        }
    }

    /** Saves the answer from the first-run question or the Settings switch. */
    fun setOptedIn(context: Context, on: Boolean) {
        val s = AppSettings(context.applicationContext)
        s.usageStats = on
        s.usageStatsAsked = true
        val wasOn = optedIn
        optedIn = on
        asked = true
        // Switched on: a fresh session, so stats from before and after aren't linked.
        if (on && !wasOn) {
            resetSession()
            trackOpened()
        }
    }

    /** Called when the app's screen is opened (not when only the alerts service runs in the background). */
    fun appOpened() = trackOpened()

    private fun trackOpened() =
        track("app_opened", mapOf("form_factor" to formFactor(), "theme" to ThemePrefs.mode.key))

    private val enabled: Boolean
        get() = available && optedIn && appContext != null

    /** Records an event if the user opted in. [props] values must be fixed strings or numbers from code. */
    fun track(name: String, props: Map<String, Any> = emptyMap()) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastEventAt > SESSION_TIMEOUT_MS) sessionId = newSessionId()
        lastEventAt = now
        val event = JSONObject()
            .put("timestamp", timestamp(now))
            .put("sessionId", sessionId)
            .put("eventName", name)
            .put("systemProps", systemProps)
            .put("props", JSONObject(props))
        queue.trySend(event)
    }

    private fun resetSession() {
        sessionId = newSessionId()
        lastEventAt = 0L
    }

    private fun send(event: JSONObject) {
        // The user may have opted out while this was queued.
        if (!enabled) return
        val request = Request.Builder()
            .url("${BuildConfig.APTABASE_HOST.trimEnd('/')}/api/v0/event")
            .header("App-Key", BuildConfig.APTABASE_KEY)
            .header("User-Agent", "CraftyCtrl/${BuildConfig.VERSION_NAME} (Android)")
            .post(event.toString().toRequestBody(JSON))
            .build()
        runCatching { http.newCall(request).execute().close() }
    }

    /** "phone" or "tablet", from the screen's smallest width. */
    private fun formFactor(): String {
        val sw = appContext?.resources?.configuration?.smallestScreenWidthDp ?: Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED
        return if (sw != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED && sw >= 600) "tablet" else "phone"
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    private fun timestamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    private val JSON = "application/json".toMediaType()
    private val SESSION_TIMEOUT_MS = TimeUnit.HOURS.toMillis(1)
}
