package com.vknxworks.craftyctrl.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.data.lock.Lock
import com.vknxworks.craftyctrl.monitor.MonitorService
import com.vknxworks.craftyctrl.ui.lock.findFragmentActivity

/**
 * Asks for Android 13's notification permission, which server alerts need.
 *
 * The returned function shows Android's dialog while Android still allows it, and opens CraftyCtrl's
 * notification settings once it doesn't any more (denied before, or notifications turned off there).
 * [onResult] gets whether notifications can be shown now; after a trip to Settings the screen checks
 * again when it comes back ([MonitorService.canNotify] on resume).
 */
@Composable
fun rememberNotifyPermission(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        latest(MonitorService.canNotify(context))
    }
    return remember(launcher) {
        {
            if (MonitorService.canNotify(context)) {
                latest(true)
            } else {
                val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                // Android shows its dialog the first time and once more after a "Don't allow"; after that
                // only Settings can turn notifications on.
                val dialogShows = permissionMissing && (
                    !settings.notifyPermissionAsked ||
                        context.findFragmentActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
                    )
                Lock.expectReturn()
                if (dialogShows) {
                    settings.notifyPermissionAsked = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                }
            }
        }
    }
}

/** CraftyCtrl's page in Android's notification settings. */
fun openNotificationSettings(context: Context) {
    Lock.expectReturn()
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
