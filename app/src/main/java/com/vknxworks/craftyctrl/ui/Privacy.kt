package com.vknxworks.craftyctrl.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vknxworks.craftyctrl.data.AppSettings

/**
 * Decides whether the window gets FLAG_SECURE (blank screenshots, screen recordings and Recents
 * thumbnail). On when the user keeps "Hide content in screenshots & recents" on (the default),
 * and always while a screen holding secrets (sign-in, file editor) is showing.
 */
object ScreenPrivacy {
    var hideContent by mutableStateOf(true)
        private set
    private var forced by mutableIntStateOf(0)

    val secure: Boolean get() = hideContent || forced > 0

    fun load(context: Context) {
        hideContent = AppSettings(context).hideContent
    }

    fun saveHideContent(context: Context, on: Boolean) {
        AppSettings(context).hideContent = on
        hideContent = on
    }

    internal fun acquire() { forced++ }
    internal fun release() { forced = (forced - 1).coerceAtLeast(0) }
}

/** Keeps FLAG_SECURE on for as long as the calling screen is shown, whatever the setting says. */
@Composable
fun SecureScreen() {
    DisposableEffect(Unit) {
        ScreenPrivacy.acquire()
        onDispose { ScreenPrivacy.release() }
    }
}

/** Copies text marked as sensitive, so Android hides it from the clipboard preview and keyboards' history. */
class SensitiveClipboard(private val context: Context) {
    fun copy(text: String, label: String = "CraftyCtrl") {
        val clip = ClipData.newPlainText(label, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
    }
}

@Composable
fun rememberSensitiveClipboard(): SensitiveClipboard {
    val context = LocalContext.current
    return remember(context) { SensitiveClipboard(context.applicationContext) }
}
