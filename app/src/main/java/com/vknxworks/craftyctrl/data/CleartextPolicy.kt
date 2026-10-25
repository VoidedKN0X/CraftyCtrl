package com.vknxworks.craftyctrl.data

import android.content.Context
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str

/**
 * Plain HTTP is allowed by the platform (network_security_config.xml), because many Crafty panels run on a
 * home network, so the app enforces it here instead: an unencrypted request only goes to a panel address
 * the user typed as http:// and accepted after the "unencrypted connection" warning. The acceptance is
 * remembered per panel (host and port). Everything else must be HTTPS.
 *
 * Installed as a network interceptor, so every redirect hop is checked: an HTTP hop is only allowed when
 * the call itself started on HTTP and that hop's own address was accepted, so an HTTPS request can never
 * be redirected down to HTTP, and the login is never sent over HTTP to a host the user didn't accept.
 */
object CleartextPolicy {
    private const val PREFS = "cleartext_policy"
    private const val KEY_ALLOWED = "allowed_origins"

    private val allowed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var prefs: android.content.SharedPreferences? = null

    private fun origin(url: HttpUrl) = "${url.host.lowercase()}:${url.port}"

    /** Loads the accepted panels. Called once at start-up. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        allowed.clear()
        allowed += p.getStringSet(KEY_ALLOWED, emptySet()).orEmpty()
    }

    fun isHttp(baseUrl: String): Boolean = baseUrl.toHttpUrlOrNull()?.isHttps == false

    /** True for https addresses, and for http addresses the user accepted. */
    fun isAccepted(baseUrl: String): Boolean {
        val url = baseUrl.toHttpUrlOrNull() ?: return false
        return isAllowed(url)
    }

    /** The user accepted plain HTTP for the panel at [baseUrl]. No-op for https addresses. */
    fun accept(baseUrl: String) {
        val url = baseUrl.toHttpUrlOrNull() ?: return
        if (url.isHttps) return
        allowed += origin(url)
        prefs?.edit()?.putStringSet(KEY_ALLOWED, HashSet(allowed))?.commit()
    }

    /** "Forgot PIN?": forget every accepted panel. */
    fun forgetAll() {
        allowed.clear()
        prefs?.edit()?.remove(KEY_ALLOWED)?.commit()
    }

    private fun isAllowed(url: HttpUrl): Boolean = url.isHttps || origin(url) in allowed

    /** For the Crafty panel's clients (REST API and the console's WebSocket). */
    val interceptor = Interceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps) {
            val original = chain.call().request().url
            if (original.isHttps || !isAllowed(original) || !isAllowed(url)) {
                throw IOException(str(R.string.err_cleartext_blocked, url.host))
            }
        }
        chain.proceed(chain.request())
    }

    /** For third-party hosts (Mojang, Crafty's download catalog): HTTPS on every hop, always. */
    val httpsOnly = Interceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps) throw IOException(str(R.string.err_cleartext_blocked, url.host))
        chain.proceed(chain.request())
    }
}
