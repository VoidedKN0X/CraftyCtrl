package com.vknxworks.craftyctrl.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App lock and alert preferences. The PIN itself lives in [com.vknxworks.craftyctrl.data.lock.LockVault];
 * the PIN hash here is only read once, to move a PIN from before CraftyCtrl 2.5.0 into the vault.
 */
class AppSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // ------------------------------------------------------------ app lock

    val pinSet: Boolean get() = prefs.contains(KEY_PIN_HASH)

    var biometricEnabled: Boolean
        get() = pinSet && prefs.getBoolean(KEY_BIOMETRIC, false)
        set(v) = prefs.edit().putBoolean(KEY_BIOMETRIC, v).apply()

    /**
     * How long the app may sit in the background before it locks again: 0 = as soon as you leave,
     * 60/300/900/3600 seconds, or -1 = only when the app restarts. Older choices (30 s, and the old
     * 30 s default) count as "as soon as you leave".
     */
    var lockAfterSeconds: Int
        get() = when (val v = prefs.getInt(KEY_LOCK_AFTER, 0)) {
            -1, 60, 300, 900, 3600 -> v
            else -> 0
        }
        set(v) = prefs.edit().putInt(KEY_LOCK_AFTER, v).apply()

    /** FLAG_SECURE for the whole app: screenshots and screen recordings show a blank screen. Off by default. */
    var hideScreenshots: Boolean
        get() = prefs.getBoolean(KEY_HIDE_SCREENSHOTS, false)
        set(v) = prefs.edit().putBoolean(KEY_HIDE_SCREENSHOTS, v).apply()

    /** A blank card in the recent-apps view instead of the app. Off by default. */
    var hideRecents: Boolean
        get() = prefs.getBoolean(KEY_HIDE_RECENTS, false)
        set(v) = prefs.edit().putBoolean(KEY_HIDE_RECENTS, v).apply()

    /**
     * Before 2.5.4 one setting, "Hide content in screenshots & recents" (on unless turned off), did both. Only a
     * choice the user made themselves is kept: turned on, it turns both new switches on; otherwise both start off.
     */
    fun migrateHideContent() {
        if (!prefs.contains(KEY_HIDE_CONTENT)) return
        val on = prefs.getBoolean(KEY_HIDE_CONTENT, false)
        val edit = prefs.edit().remove(KEY_HIDE_CONTENT)
        if (on) edit.putBoolean(KEY_HIDE_SCREENSHOTS, true).putBoolean(KEY_HIDE_RECENTS, true)
        edit.commit()
    }

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

    /** Receive Crafty's webhooks as push through ntfy (UnifiedPush). Off by default. */
    var pushEnabled: Boolean
        get() = prefs.getBoolean(KEY_PUSH, false)
        set(v) = prefs.edit().putBoolean(KEY_PUSH, v).apply()

    /**
     * How alerts run: [ALERT_MODE_MONITOR] keeps the foreground monitor (with its ongoing notification) checking
     * every [alertIntervalSeconds]; [ALERT_MODE_PUSH] has no foreground service: ntfy wakes the app, and a
     * background check runs every 15 minutes as a fallback. Push-only needs push on, otherwise it's the monitor.
     */
    var alertMode: String
        get() = if (prefs.getString(KEY_ALERT_MODE, ALERT_MODE_MONITOR) == ALERT_MODE_PUSH) ALERT_MODE_PUSH else ALERT_MODE_MONITOR
        set(v) = prefs.edit().putString(KEY_ALERT_MODE, v).apply()

    /** True when alerts run without the foreground monitor (push only, with 15-minute checks as fallback). */
    val pushOnly: Boolean get() = pushEnabled && alertMode == ALERT_MODE_PUSH

    /** Servers (by Crafty id) whose player joins and leaves are notified. None by default. */
    var playerAlertServers: Set<String>
        get() = prefs.getStringSet(KEY_PLAYER_SERVERS, emptySet()).orEmpty().toSet()
        set(v) = prefs.edit().putStringSet(KEY_PLAYER_SERVERS, v.toSet()).apply()

    fun setPlayerAlerts(serverId: String, on: Boolean) {
        playerAlertServers = if (on) playerAlertServers + serverId else playerAlertServers - serverId
    }

    /** Android's notification permission dialog was shown once; the app never asks on its own again. */
    var notifyPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_ASKED, false)
        set(v) = prefs.edit().putBoolean(KEY_NOTIFY_ASKED, v).apply()

    /** Hold a partial wake lock so checks keep running while the screen is off. */
    var alertKeepAwake: Boolean
        get() = prefs.getBoolean(KEY_ALERT_AWAKE, false)
        set(v) = prefs.edit().putBoolean(KEY_ALERT_AWAKE, v).apply()

    /**
     * CraftyCtrl 2.4.12 removed the optional anonymous stats. Deletes the two settings they used (their keys
     * start with "usage_"), so nothing from them stays on the phone. Does nothing once they're gone.
     */
    fun removeLeftoverStats() {
        val leftover = prefs.all.keys.filter { it.startsWith("usage_") }
        if (leftover.isEmpty()) return
        prefs.edit().apply { leftover.forEach { remove(it) } }.apply()
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        const val ALERT_MODE_MONITOR = "monitor"
        const val ALERT_MODE_PUSH = "push"
        private const val KEY_PUSH = "push_enabled"
        private const val KEY_ALERT_MODE = "alert_mode"
        private const val KEY_PLAYER_SERVERS = "player_alert_servers"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_LOCK_AFTER = "lock_after"
        private const val KEY_HIDE_CONTENT = "hide_content"
        private const val KEY_HIDE_SCREENSHOTS = "hide_screenshots"
        private const val KEY_HIDE_RECENTS = "hide_recents"
        private const val KEY_FAILED = "pin_failed"
        private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
        private const val KEY_LOCKOUT_REMAINING = "pin_lockout_remaining"
        private const val KEY_LOCKOUT_BASE = "pin_lockout_base"
        private const val KEY_LOCKOUT_BOOT = "pin_lockout_boot"
        private const val KEY_LIVE_REFRESH = "live_refresh"
        private const val KEY_ALERTS = "alerts_enabled"
        private const val KEY_ALERT_SHUTDOWN = "alert_shutdown"
        private const val KEY_ALERT_INTERVAL = "alert_interval"
        private const val KEY_ALERT_AWAKE = "alert_keep_awake"
        private const val KEY_NOTIFY_ASKED = "notify_permission_asked"
    }
}
