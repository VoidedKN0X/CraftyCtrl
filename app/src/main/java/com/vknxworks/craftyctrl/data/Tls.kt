package com.vknxworks.craftyctrl.data

import android.content.Context
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str

/**
 * Trust on first use for panels with a self-signed certificate (Crafty makes one for itself when it isn't
 * behind a reverse proxy). The user confirms the SHA-256 fingerprint of the panel's certificate once; after
 * that only that exact certificate (or one the phone already trusts) is accepted for that panel. Nothing is
 * ever trusted without the fingerprint being confirmed on screen.
 */
object Tls {
    /** SHA-256 of the DER-encoded certificate, as colon-separated upper-case hex. */
    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

    /** The platform trust manager (the phone's certificate authorities). */
    private fun systemTrustManager(): X509TrustManager {
        val f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        f.init(null as KeyStore?)
        return f.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    class PinMismatchException : CertificateException(str(R.string.cert_pin_mismatch))

    /** Accepts the chain only if its leaf matches [pin]; otherwise falls back to normal validation. */
    private class PinnedTrustManager(private val pin: String, private val system: X509TrustManager) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            system.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("Empty certificate chain")
            if (fingerprint(leaf).equals(pin, ignoreCase = true)) return
            try {
                system.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                throw PinMismatchException()
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
    }

    /**
     * Skips the host name check only for the panel's own [host] presenting the confirmed certificate
     * (a self-signed certificate rarely names the address it is reached at). Everything else gets the normal check.
     */
    private fun pinnedHostnameVerifier(pin: String, host: String): HostnameVerifier {
        val default = HttpsURLConnection.getDefaultHostnameVerifier()
        return HostnameVerifier { h, session ->
            val leaf = try { session.peerCertificates.firstOrNull() as? X509Certificate } catch (_: Exception) { null }
            if (h.equals(host, ignoreCase = true) && leaf != null && fingerprint(leaf).equals(pin, ignoreCase = true)) true
            else default.verify(h, session)
        }
    }

    private val pinnedClients = ConcurrentHashMap<String, OkHttpClient>()

    /**
     * [base] itself for a panel without a confirmed certificate; otherwise a client that shares [base]'s
     * connection pool, timeouts and interceptors and also accepts that panel's confirmed certificate.
     * [tag] tells apart the callers' base clients (REST and WebSocket).
     */
    fun clientFor(base: OkHttpClient, tag: String, url: HttpUrl): OkHttpClient {
        if (!url.isHttps) return base
        val pin = CertPins.pinFor(url) ?: return base
        return pinnedClients.getOrPut("$tag|${url.host.lowercase()}|$pin") {
            val tm = PinnedTrustManager(pin, systemTrustManager())
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<javax.net.ssl.TrustManager>(tm), null)
            base.newBuilder()
                .sslSocketFactory(ctx.socketFactory, tm)
                .hostnameVerifier(pinnedHostnameVerifier(pin, url.host))
                .build()
        }
    }

    private class Captured : CertificateException("captured")

    /**
     * Reads the certificate a server presents, without trusting it: the handshake is aborted as soon as
     * the chain arrives, so nothing (no login, no token) is ever sent.
     */
    fun fetchLeaf(host: String, port: Int): X509Certificate {
        var chain: Array<out X509Certificate>? = null
        val capture = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException()
            }
            override fun checkServerTrusted(c: Array<out X509Certificate>?, authType: String?) {
                chain = c
                throw Captured()
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<javax.net.ssl.TrustManager>(capture), null)
        val raw = java.net.Socket()
        try {
            raw.connect(InetSocketAddress(host, port), 10_000)
            raw.soTimeout = 10_000
            val s = ctx.socketFactory.createSocket(raw, host, port, true) as SSLSocket
            try { s.startHandshake() } catch (e: Exception) { if (chain == null) throw e } finally { runCatching { s.close() } }
        } finally {
            runCatching { raw.close() }
        }
        return chain?.firstOrNull() ?: throw CertificateException(str(R.string.cert_none))
    }

    private fun causes(e: Throwable): Sequence<Throwable> = generateSequence(e) { it.cause.takeIf { c -> c !== it } }

    /** True when the connection failed because the certificate wasn't trusted (not a network error). */
    fun isCertificateError(e: Throwable): Boolean = causes(e).any {
        it is CertificateException || it is CertPathValidatorException || it is SSLPeerUnverifiedException
    }

    /** True when the panel showed another certificate than the one that was confirmed for it. */
    fun isPinMismatch(e: Throwable): Boolean = causes(e).any { it is PinMismatchException }
}

/** What the "check this certificate" dialog shows. */
data class CertInfo(
    val baseUrl: String,
    val host: String,
    val subject: String,
    val validUntil: String,
    val fingerprint: String,
    /** A different certificate was confirmed for this panel before. */
    val replacesOther: Boolean,
)

/**
 * The certificate fingerprints the user confirmed, one per panel (host and port). Kept in their own
 * settings file; they aren't secret (anyone who can reach the panel sees its certificate).
 */
object CertPins {
    private const val PREFS = "cert_pins"
    private val pins = ConcurrentHashMap<String, String>()
    @Volatile private var prefs: android.content.SharedPreferences? = null

    private fun origin(url: HttpUrl) = "${url.host.lowercase()}:${url.port}"

    /** Loads the confirmed fingerprints. Called once at start-up. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        pins.clear()
        p.all.forEach { (k, v) -> if (v is String && v.isNotBlank()) pins[k] = v }
    }

    fun pinFor(url: HttpUrl): String? = pins[origin(url)]

    fun pinFor(baseUrl: String): String? = baseUrl.toHttpUrlOrNull()?.takeIf { it.isHttps }?.let(::pinFor)

    /** The user confirmed [fingerprint] for the panel at [baseUrl]. */
    fun accept(baseUrl: String, fingerprint: String) {
        val url = baseUrl.toHttpUrlOrNull() ?: return
        if (!url.isHttps) return
        pins[origin(url)] = fingerprint
        prefs?.edit()?.putString(origin(url), fingerprint)?.commit()
    }

    /** Back to the phone's normal certificate checks for the panel at [baseUrl]. */
    fun forget(baseUrl: String) {
        val url = baseUrl.toHttpUrlOrNull() ?: return
        pins.remove(origin(url))
        prefs?.edit()?.remove(origin(url))?.commit()
    }

    /** "Forgot PIN?": forget every confirmed certificate. */
    fun forgetAll() {
        pins.clear()
        prefs?.edit()?.clear()?.commit()
    }

    /** Reads the certificate the panel at [baseUrl] presents, for the confirmation dialog. Off the main thread. */
    fun inspect(baseUrl: String): CertInfo {
        val url = baseUrl.toHttpUrlOrNull() ?: throw CertificateException(str(R.string.cert_none))
        val leaf = Tls.fetchLeaf(url.host, url.port)
        val fp = Tls.fingerprint(leaf)
        val until = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(leaf.notAfter)
        val old = pinFor(url)
        return CertInfo(baseUrl, url.host, leaf.subjectX500Principal.name, until, fp, old != null && !old.equals(fp, ignoreCase = true))
    }
}
