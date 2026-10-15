package com.vknxworks.craftyremote.ui.lock

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
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
import com.vknxworks.craftyremote.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val PIN_MIN = 4
const val PIN_MAX = 8

fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

fun showBiometricPrompt(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .build(),
    )
}

/** Holds whether the app is locked; survives rotation, not process death (which locks again). */
class AppLockViewModel(app: Application) : AndroidViewModel(app) {
    val settings = AppSettings(app)

    var locked by mutableStateOf(settings.pinSet)
        private set
    var pinError by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var lockoutRemaining by mutableLongStateOf(0L)
        private set

    private var backgroundedAt = 0L

    init {
        refreshLockout()
    }

    /** Set when the lock screen has already shown the biometric prompt, so rotation doesn't show it twice. */
    var biometricShown = false

    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
        // "Immediately" locks before the app is hidden, so its content isn't shown on return.
        if (settings.pinSet && settings.lockAfterSeconds == 0) lockNow()
    }

    fun onForeground() {
        val since = backgroundedAt
        backgroundedAt = 0L
        if (!settings.pinSet || locked || since == 0L) return
        val away = SystemClock.elapsedRealtime() - since
        if (away >= settings.lockAfterSeconds * 1000L) lockNow()
    }

    fun lockNow() {
        if (settings.pinSet && !locked) {
            locked = true
            biometricShown = false
        }
    }

    fun unlock() {
        settings.failedAttempts = 0
        pinError = null
        locked = false
    }

    fun submitPin(pin: String, onWrong: () -> Unit) {
        if (checking || lockoutRemaining > 0) return
        viewModelScope.launch {
            checking = true
            // PBKDF2 takes a moment; keep it off the main thread.
            val ok = withContext(Dispatchers.Default) { settings.checkPin(pin) }
            checking = false
            if (ok) {
                unlock()
                return@launch
            }
            val fails = settings.failedAttempts + 1
            settings.failedAttempts = fails
            if (fails >= MAX_TRIES) {
                // 30 s, doubling for every further batch of wrong PINs, capped at 15 minutes.
                val wait = (30_000L shl ((fails - MAX_TRIES) / MAX_TRIES).coerceAtMost(5)).coerceAtMost(900_000L)
                settings.lockoutUntil = System.currentTimeMillis() + wait
                refreshLockout()
            }
            pinError = "Wrong PIN"
            onWrong()
        }
    }

    private fun refreshLockout() {
        val remaining = settings.lockoutUntil - System.currentTimeMillis()
        lockoutRemaining = remaining.coerceAtLeast(0)
        if (remaining > 0) {
            viewModelScope.launch {
                while (true) {
                    val left = settings.lockoutUntil - System.currentTimeMillis()
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
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var pin by remember { mutableStateOf("") }
    val canBio = vm.settings.biometricEnabled && activity != null && biometricAvailable(context)

    val askBiometric: () -> Unit = {
        if (canBio && activity != null) {
            runCatching { showBiometricPrompt(activity, "Unlock CraftyCtrl") { vm.unlock() } }
        }
    }
    LaunchedEffect(Unit) {
        if (!vm.biometricShown) {
            vm.biometricShown = true
            askBiometric()
        }
    }
    // Back leaves the app rather than reaching the screens underneath.
    BackHandler { activity?.moveTaskToBack(true) }

    fun press(d: Char) {
        if (vm.lockoutRemaining > 0 || vm.checking || pin.length >= PIN_MAX) return
        pin += d
        // Submit automatically once the PIN reaches its saved length.
        if (pin.length == vm.settings.pinLength) vm.submitPin(pin) { pin = "" }
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
                else -> "Enter your PIN"
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = if (vm.pinError != null || vm.lockoutRemaining > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            PinDots(pin.length)
            Spacer(Modifier.height(28.dp))
            PinPad(
                onDigit = ::press,
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                onConfirm = { if (pin.length >= PIN_MIN) vm.submitPin(pin) { pin = "" } },
                confirmEnabled = pin.length >= PIN_MIN && !vm.checking && vm.lockoutRemaining == 0L,
            )
            Spacer(Modifier.height(16.dp))
            if (canBio) {
                TextButton(onClick = askBiometric) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Use fingerprint or face")
                }
            }
            TextButton(onClick = onForgot) { Text("Forgot PIN? Sign out") }
        }
    }
}

@Composable
fun PinDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(maxOf(count, PIN_MIN)) { i ->
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
