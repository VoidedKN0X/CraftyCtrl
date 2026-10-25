package com.vknxworks.craftyctrl.data.lock

import android.content.Context
import android.os.SystemClock
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.data.CredentialStore

/**
 * Process-wide app lock state shared by the lock screen, Settings and the API client.
 *
 * The API token the screens use lives only in [liveToken]. It's filled when the app unlocks (or right
 * away when the lock is off) and wiped when it locks, so the screens underneath the lock keep their
 * state but can't reach Crafty while the app is locked.
 */
object Lock {
    @Volatile private var vaultInstance: LockVault? = null

    fun vault(context: Context): LockVault =
        vaultInstance ?: synchronized(this) {
            vaultInstance ?: LockVault(context.applicationContext, "craftyctrl").also { vaultInstance = it }
        }

    /** The token the open screens use; null while the app is locked or signed out. */
    @Volatile var liveToken: String? = null

    /** The app lock is on: a PIN is set (in the vault, or the old PIN hash not migrated yet). */
    fun isOn(context: Context): Boolean = vault(context).enabled || AppSettings(context).pinSet

    // ---------------------------------------------------------------- trips to other apps

    @Volatile private var expectReturnAt = 0L

    /**
     * The app itself is opening another app (file picker, a link, battery settings). That trip doesn't
     * count as leaving, so the app doesn't lock while you're there.
     */
    fun expectReturn() {
        expectReturnAt = SystemClock.elapsedRealtime()
    }

    /** True (once) when the app is being left within 5 seconds of [expectReturn]. */
    fun consumeExpectedTrip(): Boolean {
        val at = expectReturnAt
        expectReturnAt = 0L
        return at > 0 && SystemClock.elapsedRealtime() - at < 5_000
    }

    // ---------------------------------------------------------------- turning it on and off

    /** Turns the lock on with [pin] (6–12 digits). Call off the main thread. */
    fun enable(context: Context, pin: String) {
        val vault = vault(context)
        val store = CredentialStore(context)
        vault.setUp(pin)
        store.sealToken()
        store.syncBackgroundCopy(AppSettings(context).alertsEnabled)
    }

    /**
     * Finishes moving from the old PIN hash to the vault after the old PIN was confirmed: the vault
     * is made with [pin], the token sealed, and the old hash and fingerprint key deleted.
     */
    fun migrate(context: Context, pin: String) {
        enable(context, pin)
        AppSettings(context).clearPin()
        deleteLegacyBiometricKey()
    }

    /** Turns the lock off (after the PIN was confirmed, so the vault is open). Call off the main thread. */
    fun disable(context: Context) {
        val store = CredentialStore(context)
        store.unsealToken()
        store.syncBackgroundCopy(false)
        vault(context).disable()
        AppSettings(context).clearPin()
        deleteLegacyBiometricKey()
    }

    /** "Forgot PIN?": the token can't be decrypted any more, so sign out, forget it all and turn the lock off. */
    fun forgetEverything(context: Context) {
        val settings = AppSettings(context)
        settings.alertsEnabled = false
        CredentialStore(context).wipe()
        com.vknxworks.craftyctrl.data.CleartextPolicy.forgetAll()
        vault(context).disable()
        settings.clearPin()
        deleteLegacyBiometricKey()
        liveToken = null
    }

    /** Older versions used a separate Keystore key just to check the fingerprint. */
    private fun deleteLegacyBiometricKey() {
        runCatching {
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry("craftyctrl_biometric_unlock_key")
        }
    }

    /** PINs that are too easy to guess: one digit repeated, or a straight run up or down. */
    fun isObvious(pin: String): Boolean =
        pin.toSet().size == 1 || "0123456789012".contains(pin) || "9876543210987".contains(pin)
}
