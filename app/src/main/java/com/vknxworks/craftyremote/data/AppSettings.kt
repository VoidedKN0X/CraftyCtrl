package com.vknxworks.craftyremote.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** App lock and alert preferences. Only a salted PBKDF2 hash of the PIN is stored. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // ------------------------------------------------------------ app lock

    val pinSet: Boolean get() = prefs.contains(KEY_PIN_HASH)

    var biometricEnabled: Boolean
        get() = pinSet && prefs.getBoolean(KEY_BIOMETRIC, false)
        set(v) = prefs.edit().putBoolean(KEY_BIOMETRIC, v).apply()

    /** How long the app may sit in the background before it locks again. 0 = immediately. */
    var lockAfterSeconds: Int
        get() = prefs.getInt(KEY_LOCK_AFTER, 30)
        set(v) = prefs.edit().putInt(KEY_LOCK_AFTER, v).apply()

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, b64(salt))
            .putString(KEY_PIN_HASH, b64(hash(pin, salt)))
            .putInt(KEY_PIN_LENGTH, pin.length)
            .putInt(KEY_FAILED, 0)
            .apply()
    }

    /** Lets the lock screen submit as soon as enough digits are entered. */
    val pinLength: Int get() = prefs.getInt(KEY_PIN_LENGTH, 0)

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_LENGTH)
            .remove(KEY_PIN_HASH).remove(KEY_PIN_SALT)
            .remove(KEY_BIOMETRIC).remove(KEY_FAILED).remove(KEY_LOCKOUT_UNTIL)
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)?.let(::unb64) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null)?.let(::unb64) ?: return false
        return MessageDigest.isEqual(expected, hash(pin, salt))
    }

    /** Wrong-PIN counter that survives restarts, so killing the app doesn't reset the lockout. */
    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)
        set(v) = prefs.edit().putInt(KEY_FAILED, v).apply()

    var lockoutUntil: Long
        get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        set(v) = prefs.edit().putLong(KEY_LOCKOUT_UNTIL, v).apply()

    // ------------------------------------------------------------ alerts

    var alertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALERTS, false)
        set(v) = prefs.edit().putBoolean(KEY_ALERTS, v).apply()

    var alertPlayerJoin: Boolean
        get() = prefs.getBoolean(KEY_ALERT_JOIN, true)
        set(v) = prefs.edit().putBoolean(KEY_ALERT_JOIN, v).apply()

    var alertShutdown: Boolean
        get() = prefs.getBoolean(KEY_ALERT_SHUTDOWN, true)
        set(v) = prefs.edit().putBoolean(KEY_ALERT_SHUTDOWN, v).apply()

    var alertIntervalSeconds: Int
        get() = prefs.getInt(KEY_ALERT_INTERVAL, 30)
        set(v) = prefs.edit().putInt(KEY_ALERT_INTERVAL, v).apply()

    /** Hold a partial wake lock so checks keep running while the screen is off. */
    var alertKeepAwake: Boolean
        get() = prefs.getBoolean(KEY_ALERT_AWAKE, false)
        set(v) = prefs.edit().putBoolean(KEY_ALERT_AWAKE, v).apply()

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    private companion object {
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_LENGTH = "pin_length"
        const val KEY_BIOMETRIC = "biometric"
        const val KEY_LOCK_AFTER = "lock_after"
        const val KEY_FAILED = "pin_failed"
        const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
        const val KEY_ALERTS = "alerts_enabled"
        const val KEY_ALERT_JOIN = "alert_join"
        const val KEY_ALERT_SHUTDOWN = "alert_shutdown"
        const val KEY_ALERT_INTERVAL = "alert_interval"
        const val KEY_ALERT_AWAKE = "alert_keep_awake"
    }
}
