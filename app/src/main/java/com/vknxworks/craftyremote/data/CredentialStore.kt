package com.vknxworks.craftyremote.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AuthMode(val label: String) {
    API_KEY("API key"),
    PASSWORD("Password"),
}

data class Session(
    val baseUrl: String,
    val token: String,
    val mode: AuthMode,
    val username: String?,
)

/**
 * Keeps the bearer token encrypted with an AES key that never leaves the Android Keystore.
 * Passwords are never stored — password sign-in only keeps the token Crafty hands back.
 */
class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("crafty_session", Context.MODE_PRIVATE)

    val lastUrl: String get() = prefs.getString(KEY_URL, "").orEmpty()
    val lastUsername: String get() = prefs.getString(KEY_USER, "").orEmpty()

    fun load(): Session? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val encrypted = prefs.getString(KEY_TOKEN, null) ?: return null
        val token = decrypt(encrypted) ?: return null
        val mode = runCatching { AuthMode.valueOf(prefs.getString(KEY_MODE, "").orEmpty()) }
            .getOrDefault(AuthMode.API_KEY)
        return Session(url, token, mode, prefs.getString(KEY_USER, null))
    }

    fun save(session: Session) {
        val editor = prefs.edit()
            .putString(KEY_URL, session.baseUrl)
            .putString(KEY_TOKEN, encrypt(session.token))
            .putString(KEY_MODE, session.mode.name)
        session.username?.let { editor.putString(KEY_USER, it) }
        editor.apply()
    }

    /** Forgets the token but keeps the address and username to prefill the sign-in form. */
    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_MODE).apply()
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
        const val KEY_MODE = "auth_mode"
        const val KEY_USER = "username"
    }
}
