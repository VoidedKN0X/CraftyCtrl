package com.vknxworks.craftyremote.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vknxworks.craftyremote.data.CraftyApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Asks for the phone's fingerprint, face or other biometric (or its PIN, pattern or password)
 * before something that stops a server. Phones with no screen lock can't verify anyone, so
 * [onSuccess] runs after the normal confirmation dialog alone and [onNoLock] says so.
 */
fun verifyUser(
    activity: FragmentActivity?,
    title: String,
    subtitle: String?,
    onSuccess: () -> Unit,
    onNoLock: () -> Unit = {},
) {
    if (activity == null) {
        onSuccess()
        return
    }
    val allowed = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    if (BiometricManager.from(activity).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
        onNoLock()
        onSuccess()
        return
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .apply { subtitle?.let { setSubtitle(it) } }
        .setAllowedAuthenticators(allowed)
        .setConfirmationRequired(false)
        .build()
    BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    ).authenticate(info)
}

/** A delayed restart or stop with chat warnings counting down to it. */
data class Countdown(val action: PowerAction, val endsAt: Long, internal val job: Job)

/**
 * Runs warning countdowns outside any one screen, so leaving the server page doesn't cancel them.
 * They do stop if Android closes the app, so the warning tells players the time, not a promise.
 */
object Countdowns {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val active = mutableStateMapOf<String, Countdown>()

    private val marks = listOf(1800, 900, 600, 300, 120, 60, 30, 10, 5, 4, 3, 2, 1)

    fun start(api: CraftyApi, serverId: String, action: PowerAction, seconds: Int, note: String?, onError: (String) -> Unit) {
        cancel(api, serverId, announce = false)
        val verb = if (action == PowerAction.RESTART) "restarting" else "shutting down"
        val endsAt = System.currentTimeMillis() + seconds * 1000L
        val job = scope.launch {
            try {
                suspend fun say(text: String) = runCatching { api.sendCommand(serverId, "say $text") }
                val extra = note?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                say("Server $verb in ${human(seconds)}.$extra")
                for (m in marks.filter { it < seconds }) {
                    delay((endsAt - m * 1000L) - System.currentTimeMillis())
                    say("Server $verb in ${human(m)}.")
                }
                delay((endsAt - System.currentTimeMillis()).coerceAtLeast(0))
                api.action(serverId, action.apiName)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "The ${action.label.lowercase()} failed.")
            } finally {
                active.remove(serverId)
            }
        }
        active[serverId] = Countdown(action, endsAt, job)
    }

    fun cancel(api: CraftyApi, serverId: String, announce: Boolean = true) {
        val c = active.remove(serverId) ?: return
        c.job.cancel()
        if (announce) {
            val what = if (c.action == PowerAction.RESTART) "restart" else "shutdown"
            scope.launch { runCatching { api.sendCommand(serverId, "say The $what was cancelled.") } }
        }
    }

    fun human(s: Int): String = when {
        s >= 120 && s % 60 == 0 -> "${s / 60} minutes"
        s == 60 -> "1 minute"
        s > 60 -> "${s / 60} min ${s % 60} s"
        s == 1 -> "1 second"
        else -> "$s seconds"
    }
}
