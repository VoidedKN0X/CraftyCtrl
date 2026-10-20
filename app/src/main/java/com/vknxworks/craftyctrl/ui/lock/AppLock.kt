package com.vknxworks.craftyctrl.ui.lock

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.data.CredentialStore
import com.vknxworks.craftyctrl.data.lock.Lock
import com.vknxworks.craftyctrl.data.lock.LockVault
import androidx.compose.ui.text.style.TextAlign
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

    fun onBackground() {
        // The app opened a file picker, a link or settings itself: that trip doesn't count as leaving.
        if (Lock.consumeExpectedTrip()) {
            backgroundedAt = 0L
            return
        }
        backgroundedAt = SystemClock.elapsedRealtime()
        // Coming back should offer the fingerprint again.
        biometricShown = false
        // "Immediately" locks before the app is hidden, so its content and key are gone right away.
        if (lockOn && settings.lockAfterSeconds == 0) lockNow()
    }

    fun onForeground() {
        val since = backgroundedAt
        backgroundedAt = 0L
        val after = settings.lockAfterSeconds
        if (!lockOn || locked || since == 0L || after < 0) return
        val away = SystemClock.elapsedRealtime() - since
        if (away >= after * 1000L) lockNow()
    }

    fun lockNow() {
        if (lockOn && !locked) {
            locked = true
            biometricShown = false
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
        if (vault.unlockWithBiometric(cipher)) unlocked() else pinError = "Fingerprint didn't unlock. Use your PIN."
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

    /** The new PIN chosen after an old short one; moves the lock into the vault with it. */
    fun chooseNewPin(pin: String, onFailed: (String) -> Unit) {
        if (checking || oldPin == null) return
        viewModelScope.launch {
            checking = true
            val moved = withContext(Dispatchers.Default) { runCatching { Lock.migrate(getApplication<Application>(), pin) }.isSuccess }
            checking = false
            if (moved) unlocked() else onFailed("Couldn't save the PIN. Try again.")
        }
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

@Composable
fun LockScreen(vm: AppLockViewModel, onForgot: () -> Unit) {
    if (vm.mustChoosePin) {
        ChooseNewPin(vm)
        return
    }
    val context = LocalContext.current
    // The lock screen lives in its own window (a dialog), whose context wraps the activity rather
    // than being it, so look through the wrappers.
    val activity = remember(context) { context.findFragmentActivity() }
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

    fun press(d: Char) {
        if (vm.lockoutRemaining > 0 || vm.checking || pin.length >= PIN_MAX) return
        pin += d
        // Submit automatically once the PIN reaches its saved length.
        val length = if (vm.legacy) vm.settings.pinLength else vm.vault.pinLength
        if (pin.length == length) vm.submitPin(pin) { pin = "" }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text("CraftyCtrl is locked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            val sub = when {
                vm.lockoutRemaining > 0 -> "Too many tries. Try again in ${(vm.lockoutRemaining + 999) / 1000}s"
                vm.pinError != null -> vm.pinError!!
                vm.legacy -> "Enter your PIN to finish the security update"
                vm.biometricInvalidated -> "A fingerprint was added or removed. Enter your PIN, then turn fingerprint unlock on again in Settings."
                pinDue -> "Enter your PIN. It's needed after a restart and every 72 hours."
                else -> "Enter your PIN"
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = if (vm.pinError != null || vm.lockoutRemaining > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            PinDots(pin.length)
            Spacer(Modifier.height(28.dp))
            val min = if (vm.legacy) PIN_MIN_LEGACY else PIN_MIN
            PinPad(
                onDigit = ::press,
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                onConfirm = { if (pin.length >= min) vm.submitPin(pin) { pin = "" } },
                confirmEnabled = pin.length >= min && !vm.checking && vm.lockoutRemaining == 0L,
            )
            Spacer(Modifier.height(16.dp))
            if (canBio) {
                TextButton(onClick = askBiometric) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Use fingerprint")
                }
            }
            TextButton(onClick = { forgetting = true }) { Text("Forgot PIN? Sign out") }
        }
    }
    if (forgetting) {
        AlertDialog(
            onDismissRequest = { forgetting = false },
            title = { Text("Forgot your PIN?") },
            text = {
                Text(
                    "The PIN can't be recovered, because it encrypts your Crafty login. CraftyCtrl will sign out, " +
                        "turn off server alerts and forget the panel address and login on this phone. Your servers on Crafty aren't touched.",
                )
            },
            confirmButton = {
                TextButton(onClick = { forgetting = false; onForgot() }) {
                    Text("Sign out and reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { forgetting = false }) { Text("Cancel") } },
        )
    }
}

/** After unlocking with an old 4- or 5-digit PIN: choose a new one (twice) before the app opens. */
@Composable
private fun ChooseNewPin(vm: AppLockViewModel) {
    val activity = LocalContext.current.findFragmentActivity()
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler { activity?.moveTaskToBack(true) }

    fun next() {
        if (vm.checking || pin.length < PIN_MIN) return
        val f = first
        when {
            f == null && Lock.isObvious(pin) -> { message = "Too easy to guess. Try a less obvious PIN."; pin = "" }
            f == null -> { first = pin; pin = ""; message = null }
            f != pin -> { first = null; pin = ""; message = "The PINs didn't match. Start again." }
            else -> vm.chooseNewPin(pin) { message = it; first = null; pin = "" }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text("Choose a new PIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                message ?: if (first == null) "PINs now need $PIN_MIN to $PIN_MAX digits. It also encrypts your Crafty login while the app is locked."
                else "Enter the same PIN again",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            PinDots(pin.length)
            Spacer(Modifier.height(28.dp))
            PinPad(
                onDigit = { d -> if (pin.length < PIN_MAX && !vm.checking) { pin += d; message = null } },
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                onConfirm = ::next,
                confirmEnabled = pin.length >= PIN_MIN && !vm.checking,
            )
        }
    }
}

@Composable
fun PinDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(maxOf(count, PIN_MIN_LEGACY)) { i ->
            val filled = i < count
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { d -> DigitKey(d.toString()) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackspace, modifier = Modifier.size(68.dp)) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
            }
            DigitKey("0") { onDigit('0') }
            TextButton(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.size(68.dp)) { Text("OK") }
        }
    }
}

@Composable
private fun DigitKey(label: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(68.dp)) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}
