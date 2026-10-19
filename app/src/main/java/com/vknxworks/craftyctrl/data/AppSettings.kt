package com.vknxworks.craftyctrl.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** App lock and alert preferences. Only a salted PBKDF2 hash of the PIN is stored. */
class AppSettings(context: Context) {
    private val appContext = context.applicationContext
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

    /** FLAG_SECURE for the whole app: blank screenshots and Recents thumbnail. On by default. */
    var hideContent: Boolean
        get() = prefs.getBoolean(KEY_HIDE_CONTENT, true)
        set(v) = prefs.edit().putBoolean(KEY_HIDE_CONTENT, v).apply()

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
            .remove(KEY_LOCKOUT_REMAINING).remove(KEY_LOCKOUT_BASE).remove(KEY_LOCKOUT_BOOT)
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)?.let(::unb64) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null)?.let(::unb64) ?: return false
        return MessageDigest.isEqual(expected, hash(pin, salt))
    }

    /**
     * Wrong-PIN counter that survives restarts, so killing the app doesn't reset the lockout.
     * Written synchronously: the lock screen counts an attempt before checking it, so closing the
     * app the moment "Wrong PIN" shows can't undo the count.
     */
    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)
        set(v) { prefs.edit().putInt(KEY_FAILED, v).commit() }

    /**
     * Wrong-PIN lockout, measured with the monotonic clock so changing the date/time can't end it. The remaining
     * time is saved with the elapsedRealtime and boot count it was measured at; after a reboot (boot count
     * changed, or elapsedRealtime went backwards) the remaining time continues from the last checkpoint.
     */
    fun startLockout(durationMs: Long) {
        prefs.edit()
            .putLong(KEY_LOCKOUT_REMAINING, durationMs)
            .putLong(KEY_LOCKOUT_BASE, SystemClock.elapsedRealtime())
            .putInt(KEY_LOCKOUT_BOOT, bootCount())
            .remove(KEY_LOCKOUT_UNTIL)
            .commit()
    }

    /** Milliseconds of lockout left (0 when none); also checkpoints the remaining time for reboots. */
    fun lockoutRemaining(): Long {
        migrateWallClockLockout()
        val remaining = prefs.getLong(KEY_LOCKOUT_REMAINING, 0L)
        if (remaining <= 0L) return 0L
        val now = SystemClock.elapsedRealtime()
        val base = prefs.getLong(KEY_LOCKOUT_BASE, now)
        val rebooted = prefs.getInt(KEY_LOCKOUT_BOOT, -1) != bootCount() || now < base
        val left = if (rebooted) remaining else (remaining - (now - base)).coerceAtLeast(0L)
        val edit = prefs.edit()
        if (left <= 0L) {
            edit.remove(KEY_LOCKOUT_REMAINING).remove(KEY_LOCKOUT_BASE).remove(KEY_LOCKOUT_BOOT)
        } else {
            edit.putLong(KEY_LOCKOUT_REMAINING, left).putLong(KEY_LOCKOUT_BASE, now).putInt(KEY_LOCKOUT_BOOT, bootCount())
        }
        edit.apply()
        return left
    }

    /** Older versions stored the lockout end as wall-clock time; carry over what's left of it. */
    private fun migrateWallClockLockout() {
        if (!prefs.contains(KEY_LOCKOUT_UNTIL)) return
        val left = (prefs.getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis()).coerceIn(0L, 900_000L)
        prefs.edit().remove(KEY_LOCKOUT_UNTIL).apply()
        if (left > 0L) startLockout(left)
    }

    private fun bootCount(): Int =
        runCatching { Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    // ------------------------------------------------------------ in-app refresh

    /** How often open screens ask Crafty for fresh stats (separate from background alerts). */
    var liveRefreshSeconds: Int
        get() = prefs.getInt(KEY_LIVE_REFRESH, 2)
        set(v) = prefs.edit().putInt(KEY_LIVE_REFRESH, v).apply()

    // ------------------------------------------------------------ alerts

    var alertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALERTS, false)
        set(v) = prefs.edit().putBoolean(KEY_ALERTS, v).apply()

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

    // ------------------------------------------------------------ usage stats

    /** Opt-in anonymous usage stats (off until the user says yes). */
    var usageStats: Boolean
        get() = prefs.getBoolean(KEY_USAGE_STATS, false)
        set(v) = prefs.edit().putBoolean(KEY_USAGE_STATS, v).apply()

    /** Whether the one-time usage stats question has been answered. */
    var usageStatsAsked: Boolean
        get() = prefs.getBoolean(KEY_USAGE_STATS_ASKED, false)
        set(v) = prefs.edit().putBoolean(KEY_USAGE_STATS_ASKED, v).apply()

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
        const val KEY_HIDE_CONTENT = "hide_content"
        const val KEY_FAILED = "pin_failed"
        const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
        const val KEY_LOCKOUT_REMAINING = "pin_lockout_remaining"
        const val KEY_LOCKOUT_BASE = "pin_lockout_base"
        const val KEY_LOCKOUT_BOOT = "pin_lockout_boot"
        const val KEY_LIVE_REFRESH = "live_refresh"
        const val KEY_ALERTS = "alerts_enabled"
        const val KEY_ALERT_SHUTDOWN = "alert_shutdown"
        const val KEY_ALERT_INTERVAL = "alert_interval"
        const val KEY_ALERT_AWAKE = "alert_keep_awake"
        const val KEY_USAGE_STATS = "usage_stats"
        const val KEY_USAGE_STATS_ASKED = "usage_stats_asked"
    }
}
