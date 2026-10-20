package com.vknxworks.craftyctrl.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.vknxworks.craftyctrl.data.lock.Lock
import com.vknxworks.craftyctrl.data.lock.LockVault
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AuthMode(val label: String) {
    API_KEY("API key"),
    PASSWORD("Password"),
}

/**
 * Who is signed in. The token itself isn't part of it: the screens get it from [com.vknxworks.craftyctrl.data.lock.Lock.liveToken],
 * which is wiped while the app is locked.
 */
data class Session(
    val baseUrl: String,
    val mode: AuthMode,
    val username: String?,
)

/**
 * Keeps the bearer token encrypted with an AES key that never leaves the Android Keystore. With the app
 * lock on, the token is also encrypted with the lock's vault key, so it can only be read after unlocking.
 * Passwords are never stored — password sign-in only keeps the token Crafty hands back.
 */
class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("crafty_session", Context.MODE_PRIVATE)
    private val vault = Lock.vault(context)

    val lastUrl: String get() = prefs.getString(KEY_URL, "").orEmpty()
    val lastUsername: String get() = prefs.getString(KEY_USER, "").orEmpty()

    /** The signed-in server, or null when signed out. Works while locked; the token isn't needed. */
    fun load(): Session? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        if (!prefs.contains(KEY_TOKEN)) return null
        val mode = runCatching { AuthMode.valueOf(prefs.getString(KEY_MODE, "").orEmpty()) }
            .getOrDefault(AuthMode.API_KEY)
        return Session(url, mode, prefs.getString(KEY_USER, null))
    }

    /** The token, or null while it's sealed by the app lock and the app is locked. */
    fun token(): String? {
        val plain = prefs.getString(KEY_TOKEN, null)?.let(::decrypt) ?: return null
        return if (LockVault.isSealed(plain)) vault.open(plain) else plain
    }

    /**
     * The token for server alerts, which run with the app closed or locked. While the lock is on and
     * alerts are on, they use their own Keystore-only copy (see [syncBackgroundCopy]).
     */
    fun backgroundToken(): String? {
        val plain = prefs.getString(KEY_TOKEN, null)?.let(::decrypt) ?: return null
        if (!LockVault.isSealed(plain)) return plain
        return vault.open(plain) ?: prefs.getString(KEY_TOKEN_BG, null)?.let(::decrypt)
    }

    fun save(session: Session, token: String) {
        val editor = prefs.edit()
            .putString(KEY_URL, session.baseUrl)
            .putString(KEY_TOKEN, encrypt(wrap(token)))
            .putString(KEY_MODE, session.mode.name)
        session.username?.let { editor.putString(KEY_USER, it) }
        editor.commit()
    }

    private fun wrap(token: String): String =
        if (vault.enabled) vault.seal(token) ?: error("Unlock CraftyCtrl first") else token

    /** The app lock was turned on (vault open): encrypt the saved token with its key too. */
    fun sealToken() {
        val token = token() ?: return
        prefs.edit().putString(KEY_TOKEN, encrypt(wrap(token))).commit()
    }

    /** The app lock is being turned off (vault open): back to Keystore-only encryption. */
    fun unsealToken() {
        val token = token() ?: return
        prefs.edit().putString(KEY_TOKEN, encrypt(token)).commit()
    }

    /**
     * Server alerts need the token while the app is locked. With the lock and alerts both on, they get a
     * Keystore-only copy of it; otherwise the copy is deleted. Only changes it while the token can be read.
     */
    fun syncBackgroundCopy(alertsOn: Boolean) {
        if (vault.enabled && alertsOn) {
            val token = token() ?: return
            prefs.edit().putString(KEY_TOKEN_BG, encrypt(token)).commit()
        } else {
            prefs.edit().remove(KEY_TOKEN_BG).commit()
        }
    }

    /** Forgets the token but keeps the address and username to prefill the sign-in form. */
    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_TOKEN_BG).remove(KEY_MODE).commit()
    }

    /** Forgets everything, including the address and username ("Forgot PIN?"). */
    fun wipe() {
        prefs.edit().clear().commit()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(cipher.iv) + ":" + b64(sealed)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val (iv, sealed) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, unb64(iv)))
        String(cipher.doFinal(unb64(sealed)), Charsets.UTF_8)
    }.getOrNull()

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "crafty_remote_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_URL = "base_url"
        const val KEY_TOKEN = "token"
        const val KEY_TOKEN_BG = "token_alerts"
        const val KEY_MODE = "auth_mode"
        const val KEY_USER = "username"
    }
}
