package com.vknxworks.craftyctrl.ui.lock

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.data.CredentialStore
import com.vknxworks.craftyctrl.data.lock.Lock
import com.vknxworks.craftyctrl.data.lock.LockVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.repeatOnLifecycle
import javax.crypto.Cipher

/** Shortest PIN that can be set now. */
const val PIN_MIN = LockVault.PIN_MIN
/** Shortest PIN the lock screen accepts, so a 4- or 5-digit PIN from before 2.5.0 still unlocks once. */
const val PIN_MIN_LEGACY = 4
const val PIN_MAX = LockVault.PIN_MAX

/** Only class 3 ("strong") biometrics: camera-only face unlock can be fooled by a photo. */
fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

/** The FragmentActivity behind [this] context, looking through wrappers such as a dialog's. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c != null) {
        if (c is FragmentActivity) return c
        c = (c as? android.content.ContextWrapper)?.baseContext
    }
    return null
}

/**
 * Shows a strong-biometric prompt for [cipher], a vault Keystore key that only works right after a real
 * match. [onSuccess] gets the unlocked cipher; a faked "success" without a match can't use it.
 */
fun showVaultPrompt(
    activity: FragmentActivity,
    title: String,
    cipher: Cipher,
    subtitle: String? = null,
    onDismissed: (systemCancelled: Boolean) -> Unit = {},
    onSuccess: (Cipher) -> Unit,
) {
    // Android silently ignores a prompt requested while the screen is in the background, so the
    // caller must only ask while it's showing; report that it didn't open so it can try again.
    if (activity.supportFragmentManager.isStateSaved) {
        onDismissed(true)
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(onSuccess)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Cancelled by the system (the app was hidden, the screen turned off, ...) rather than by the user.
                onDismissed(errorCode == BiometricPrompt.ERROR_CANCELED)
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build(),
        BiometricPrompt.CryptoObject(cipher),
    )
}

/**
 * Whether the app is locked; survives rotation, not process death (which locks again). Locking wipes
 * the vault key and the token the screens use; the screens themselves stay underneath, so open pages
 * and unsaved edits survive the lock.
 */
class AppLockViewModel(app: Application) : AndroidViewModel(app) {
    val settings = AppSettings(app)
    val vault = Lock.vault(app)
    private val store = CredentialStore(app)

    /** A PIN is set: in the vault, or an old PIN not moved into the vault yet. */
    val lockOn: Boolean get() = vault.enabled || settings.pinSet
    /** The old PIN from before 2.5.0 is still in use; the next correct PIN moves it into the vault. */
    val legacy: Boolean get() = !vault.enabled && settings.pinSet

    var locked by mutableStateOf(lockOn)
        private set
    var pinError by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var lockoutRemaining by mutableLongStateOf(0L)
        private set

    /** The old PIN was shorter than 6 digits: a new one has to be chosen before the app opens. */
    var mustChoosePin by mutableStateOf(false)
        private set
    private var oldPin: String? = null

    private var backgroundedAt = 0L

    init {
        refreshLockout()
    }

    /** Set when the lock screen has already shown the biometric prompt, so rotation doesn't show it twice. */
    var biometricShown = false

    /** Set when the biometric key was invalidated by a new fingerprint; the PIN is needed. */
    var biometricInvalidated by mutableStateOf(false)

    private fun lockoutLeft(): Long = if (vault.enabled) vault.lockoutRemaining() else settings.lockoutRemaining()

    /** The current trip away was started by the app itself (file picker, a link, settings). */
    private var exemptTrip = false

    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
        // The app opened a file picker, a link or settings itself: that trip doesn't count as leaving
        // (unless you stay away long, see onForeground).
        exemptTrip = Lock.consumeExpectedTrip()
        if (exemptTrip) return
        // Coming back should offer the fingerprint again.
        biometricShown = false
        // "Immediately" locks before the app is hidden, so its content and key are gone right away.
        if (lockOn && settings.lockAfterSeconds == 0) lockNow()
    }

    fun onForeground() {
        val since = backgroundedAt
        val exempt = exemptTrip
        backgroundedAt = 0L
        exemptTrip = false
        val after = settings.lockAfterSeconds
        if (!lockOn || locked || since == 0L || after < 0) return
        val away = SystemClock.elapsedRealtime() - since
        // A trip the app started still locks if you stay away long: after 5 minutes, or the chosen time if longer.
        val limit = if (exempt) maxOf(after * 1000L, 5 * 60_000L) else after * 1000L
        if (away >= limit) lockNow()
    }

    fun lockNow() {
        if (lockOn && !locked) {
            locked = true
            biometricShown = false
            PinRequests.request = null
            vault.close()
            Lock.liveToken = null
        }
    }

    private fun unlocked() {
        settings.failedAttempts = 0
        pinError = null
        mustChoosePin = false
        oldPin = null
        Lock.liveToken = store.token()
        locked = false
    }

    /** Fingerprint unlock: the prompt's cipher has to decrypt the vault key. */
    fun unlockWithBiometric(cipher: Cipher) {
        if (vault.unlockWithBiometric(cipher)) unlocked() else pinError = "Couldn't unlock with your fingerprint. Use your PIN."
    }

    fun submitPin(pin: String, onWrong: () -> Unit) {
        // Asks the stored lockout (monotonic clock), not the on-screen countdown, which may not have caught up yet.
        if (checking || lockoutLeft() > 0) return
        viewModelScope.launch {
            checking = true
            if (!legacy) {
                // Slow on purpose (it makes guessing slow too), so off the main thread.
                val result = withContext(Dispatchers.Default) { vault.unlockWithPin(pin) }
                checking = false
                if (result == LockVault.PinResult.Ok) {
                    unlocked()
                } else {
                    refreshLockout()
                    pinError = "Wrong PIN"
                    onWrong()
                }
                return@launch
            }
            // The old PIN hash: count the attempt before checking it, so closing the app mid-check can't skip the count.
            val fails = withContext(Dispatchers.IO) {
                (settings.failedAttempts + 1).also { settings.failedAttempts = it }
            }
            val ok = withContext(Dispatchers.Default) { settings.checkPin(pin) }
            if (ok) {
                settings.failedAttempts = 0
                if (pin.length >= PIN_MIN && !Lock.isObvious(pin)) {
                    // Moves the PIN into the vault and seals the token with it.
                    val moved = withContext(Dispatchers.Default) { runCatching { Lock.migrate(getApplication<Application>(), pin) }.isSuccess }
                    checking = false
                    if (moved) unlocked() else { pinError = "Couldn't finish the security update. Try again."; onWrong() }
                } else {
                    checking = false
                    oldPin = pin
                    pinError = null
                    mustChoosePin = true
                }
                return@launch
            }
            checking = false
            if (fails >= MAX_TRIES) {
                // 30 s, doubling for every further batch of wrong PINs, capped at 15 minutes.
                val wait = (30_000L shl ((fails - MAX_TRIES) / MAX_TRIES).coerceAtMost(5)).coerceAtMost(900_000L)
                settings.startLockout(wait)
                refreshLockout()
            }
            pinError = "Wrong PIN"
            onWrong()
        }
    }

    /** The new PIN chosen after an old short one; moves the lock into the vault with it. Throws when it couldn't. */
    suspend fun chooseNewPin(pin: String) {
        check(oldPin != null)
        withContext(Dispatchers.Default) { Lock.migrate(getApplication<Application>(), pin) }
        unlocked()
    }

    /** Typing again clears "Wrong PIN". */
    fun clearError() {
        pinError = null
    }

    /** "Forgot PIN?": signs out and forgets the token, the PIN and the vault. */
    fun forget() {
        Lock.forgetEverything(getApplication<Application>())
        unlocked()
    }

    private var ticker: kotlinx.coroutines.Job? = null

    private fun refreshLockout() {
        val remaining = lockoutLeft()
        lockoutRemaining = remaining.coerceAtLeast(0)
        if (remaining > 0 && ticker?.isActive != true) {
            ticker = viewModelScope.launch {
                while (true) {
                    val left = lockoutLeft()
                    lockoutRemaining = left.coerceAtLeast(0)
                    if (left <= 0) break
                    delay(500)
                }
            }
        }
    }

    private companion object {
        const val MAX_TRIES = 5
    }
}

/** The lock screen: PIN pad, fingerprint, and "Forgot PIN?" (the same look and flow as every Ctrl app). */
@Composable
fun LockScreen(vm: AppLockViewModel, onForgot: () -> Unit) {
    if (vm.mustChoosePin) {
        // Unlocked with an old 4- or 5-digit PIN: choose a new one (twice) before the app opens.
        PinSetupScreen(
            vault = vm.vault,
            title = "Choose an app PIN",
            intro = "PINs now need at least $PIN_MIN digits, different from your phone's PIN. " +
                "Your Crafty login is encrypted with it while the app is locked.",
            verifyCurrent = false,
            cancellable = false,
            onDone = { pin -> vm.chooseNewPin(pin) },
            onCancel = {},
        )
        return
    }
    val context = LocalContext.current
    // The lock screen lives in its own window (a dialog), whose context wraps the activity rather
    // than being it, so look through the wrappers.
    val activity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    var pin by remember { mutableStateOf("") }
    var forgetting by remember { mutableStateOf(false) }
    val pinDue = vm.vault.biometricEnabled && vm.vault.pinRequired()
    val canBio = !vm.legacy && !vm.biometricInvalidated && vm.vault.biometricEnabled && !pinDue &&
        activity != null && biometricAvailable(context)

    val askBiometric: () -> Unit = {
        if (canBio && activity != null) {
            val cipher = vm.vault.biometricUnlockCipher()
            if (cipher == null) {
                // A fingerprint was added or removed (or the PIN is due): ask for the PIN.
                vm.biometricInvalidated = !vm.vault.biometricEnabled
            } else {
                runCatching {
                    showVaultPrompt(
                        activity,
                        "Unlock CraftyCtrl",
                        cipher,
                        subtitle = "Confirm it's you to manage your servers",
                        // Not shown, or closed by the system: offer it again next time the app is on screen.
                        onDismissed = { system -> if (system) vm.biometricShown = false },
                    ) { unlocked -> vm.unlockWithBiometric(unlocked) }
                }.onFailure { vm.biometricShown = false }
            }
        }
    }
    // Ask only once the lock screen is actually in front (resumed). The lock can engage while the
    // app is going to the background, and a prompt requested then would never appear.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, canBio) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            if (!vm.biometricShown) {
                vm.biometricShown = true
                askBiometric()
            }
        }
    }
    // Back leaves the app rather than reaching the screens underneath.
    BackHandler { activity?.moveTaskToBack(true) }

    val wrong: () -> Unit = {
        pin = ""
        scope.shakePin(shake, haptics)
    }
    // Submits automatically once the PIN reaches its saved length.
    val savedLength = if (vm.legacy) vm.settings.pinLength else vm.vault.pinLength
    val min = if (vm.legacy) PIN_MIN_LEGACY else PIN_MIN

    PinPadScreen(
        title = "CraftyCtrl is locked",
        message = when {
            vm.lockoutRemaining > 0 -> lockoutText(vm.lockoutRemaining)
            vm.pinError != null -> vm.pinError!!
            vm.legacy -> "Enter your PIN to finish the security update"
            vm.biometricInvalidated -> "A fingerprint was added or removed. Unlock with your PIN, then turn fingerprint unlock on again."
            pinDue -> "Enter your PIN. It's needed after a restart and every 72 hours."
            else -> "Enter your PIN"
        },
        isError = vm.pinError != null || vm.lockoutRemaining > 0 || vm.biometricInvalidated,
        pin = pin,
        shake = shake.value,
        checking = vm.checking,
        enabled = vm.lockoutRemaining == 0L && !vm.checking,
        onDigit = { d ->
            if (pin.length < PIN_MAX) {
                vm.clearError()
                pin += d
                if (pin.length == savedLength) vm.submitPin(pin, wrong)
            }
        },
        onBackspace = { pin = pin.dropLast(1) },
        onConfirm = { if (pin.length >= min) vm.submitPin(pin, wrong) },
        showConfirm = pin.length >= min && pin.length != savedLength,
        minDots = if (vm.legacy) maxOf(PIN_MIN_LEGACY, savedLength) else PIN_MIN,
        bottom = {
            LockScreenButtons(showFingerprint = canBio, onFingerprint = askBiometric, onForgot = { forgetting = true })
        },
    )
    if (forgetting) {
        ForgotPinDialog(
            body = "The PIN can't be recovered, because it encrypts your Crafty login. CraftyCtrl will sign out, " +
                "turn off server alerts and forget the panel address and login on this phone. Your servers on Crafty aren't touched.",
            confirm = "Sign out and reset",
            onDismiss = { forgetting = false },
        ) {
            forgetting = false
            onForgot()
        }
    }
}

/** Turning the lock on or off, or changing the PIN, from Settings: a PIN screen on top of the app. */
@Composable
fun PinRequestScreen(request: PinRequest, vm: AppLockViewModel) {
    val app = LocalContext.current.applicationContext
    val done = { PinRequests.request = null }
    when (request) {
        PinRequest.ENABLE -> PinSetupScreen(
            vault = vm.vault,
            title = "Choose an app PIN",
            intro = "At least $PIN_MIN digits, different from your phone's PIN. " +
                "If you forget it, the only way back in is to sign out and sign in again.",
            verifyCurrent = false,
            cancellable = true,
            onDone = { pin -> withContext(Dispatchers.Default) { Lock.enable(app, pin) }; done() },
            onCancel = done,
        )
        PinRequest.CHANGE -> PinSetupScreen(
            vault = vm.vault,
            title = "Change app PIN",
            intro = "At least $PIN_MIN digits, different from your phone's PIN.",
            verifyCurrent = true,
            cancellable = true,
            onDone = { pin -> withContext(Dispatchers.Default) { check(vm.vault.changePin(pin)) }; done() },
            onCancel = done,
        )
        PinRequest.DISABLE -> PinSetupScreen(
            vault = vm.vault,
            title = "Turn off app lock",
            intro = "Enter your app PIN to turn the lock off.",
            verifyCurrent = true,
            verifyOnly = true,
            cancellable = true,
            onDone = { withContext(Dispatchers.Default) { Lock.disable(app) }; done() },
            onCancel = done,
        )
    }
}

/** The lock and PIN screens are always kept out of screenshots, recordings and casting, like sign-in. */
@Composable
internal fun KeepOutOfScreenshots() = com.vknxworks.craftyctrl.ui.SecureScreen()
