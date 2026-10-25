@file:OptIn(ExperimentalMaterial3Api::class)

package com.vknxworks.craftyctrl.ui.screens

import com.vknxworks.craftyctrl.ui.components.CtrlTopBar
import com.vknxworks.craftyctrl.ui.components.CtrlBackButton
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.data.CredentialStore
import com.vknxworks.craftyctrl.data.lock.Lock
import com.vknxworks.craftyctrl.data.lock.LockVault
import com.vknxworks.craftyctrl.monitor.MonitorService
import com.vknxworks.craftyctrl.ui.ScreenPrivacy
import com.vknxworks.craftyctrl.ui.rememberNotifyPermission
import com.vknxworks.craftyctrl.ui.lock.PinRequest
import com.vknxworks.craftyctrl.ui.lock.PinRequests
import com.vknxworks.craftyctrl.ui.lock.biometricAvailable
import com.vknxworks.craftyctrl.ui.lock.findFragmentActivity
import com.vknxworks.craftyctrl.ui.lock.showVaultPrompt
import com.vknxworks.craftyctrl.ui.settings.Group
import com.vknxworks.craftyctrl.ui.settings.GroupChoice
import com.vknxworks.craftyctrl.ui.settings.GroupRow
import com.vknxworks.craftyctrl.ui.settings.GroupSwitch
import androidx.compose.ui.res.pluralStringResource

/** When the app lock asks again after you leave the app (seconds; -1 = only when the app restarts). */
private val LOCK_AGAIN = listOf(
    0 to R.string.lock_again_immediately,
    60 to R.string.lock_again_1m,
    300 to R.string.lock_again_5m,
    900 to R.string.lock_again_15m,
    3600 to R.string.lock_again_1h,
    -1 to R.string.lock_again_restart,
)

/** Settings, opened from the You page (tap your profile picture). About is on the You page itself. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }

    val vault = remember { Lock.vault(context) }
    val store = remember { CredentialStore(context) }
    val lockOn = vault.enabled
    var lockAfter by remember { mutableIntStateOf(settings.lockAfterSeconds) }

    var liveRefresh by remember { mutableIntStateOf(settings.liveRefreshSeconds) }
    var alerts by remember { mutableStateOf(settings.alertsEnabled) }
    var interval by remember { mutableIntStateOf(settings.alertIntervalSeconds) }
    var keepAwake by remember { mutableStateOf(settings.alertKeepAwake) }
    var notifDenied by remember { mutableStateOf(false) }
    var canNotify by remember { mutableStateOf(MonitorService.canNotify(context)) }

    val bioAvailable = remember { biometricAvailable(context) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        alerts = settings.alertsEnabled
        // Back from Android's settings: notifications may have been allowed or blocked there.
        canNotify = MonitorService.canNotify(context)
        if (canNotify) notifDenied = false
        MonitorService.sync(context)
    }

    fun turnAlertsOn() {
        settings.alertsEnabled = true
        alerts = true
        // With the app lock on, alerts get their own copy of the login so they work while locked.
        store.syncBackgroundCopy(true)
        MonitorService.sync(context)
    }

    // Android's dialog while it still shows, otherwise CraftyCtrl's notification settings.
    val askNotify = rememberNotifyPermission { granted ->
        canNotify = granted
        notifDenied = !granted
        if (granted) turnAlertsOn()
    }

    Scaffold(
        topBar = {
            CtrlTopBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    CtrlBackButton(onClick = onBack)
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Group(stringResource(R.string.notifications)) {
                // Alerts that are on but can't notify are paused, and say so instead of showing "on".
                if (!canNotify && (alerts || notifDenied)) {
                    GroupRow(
                        stringResource(R.string.notifications_blocked),
                        stringResource(if (alerts) R.string.notifications_blocked_alerts_paused else R.string.notifications_are_blocked_for_this_app),
                        icon = Icons.Outlined.NotificationsOff,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = askNotify,
                        trailing = { TextButton(onClick = askNotify) { Text(stringResource(R.string.allow), color = com.vknxworks.craftyctrl.ui.theme.CtrlColors.Accent) } },
                    )
                }
                if (!canNotify && alerts) {
                    // Paused alerts can still be switched off for good, so the warning doesn't stay.
                    GroupRow(
                        stringResource(R.string.turn_off_server_alerts),
                        icon = Icons.Outlined.NotificationsActive,
                        onClick = {
                            settings.alertsEnabled = false
                            alerts = false
                            store.syncBackgroundCopy(false)
                            MonitorService.sync(context)
                        },
                    )
                }
                GroupSwitch(
                    stringResource(R.string.server_alerts),
                    stringResource(R.string.get_notified_when_a_server_stops),
                    alerts && canNotify,
                    icon = Icons.Outlined.NotificationsActive,
                ) { on ->
                    if (on) {
                        if (!MonitorService.canNotify(context)) {
                            askNotify()
                        } else {
                            canNotify = true
                            turnAlertsOn()
                        }
                    } else {
                        settings.alertsEnabled = false
                        alerts = false
                        store.syncBackgroundCopy(false)
                        MonitorService.sync(context)
                    }
                }
                GroupChoice(
                    stringResource(R.string.check_every),
                    listOf(15, 30).map { it to pluralStringResource(R.plurals.x_seconds, it, it) } + listOf(1, 5).map { it * 60 to pluralStringResource(R.plurals.x_minutes, it, it) },
                    interval,
                    icon = Icons.Outlined.Schedule,
                ) { settings.alertIntervalSeconds = it; interval = it }
                GroupSwitch(
                    stringResource(R.string.keep_checking_while_the_screen_is),
                    stringResource(R.string.without_this_android_may_pause_checks),
                    keepAwake,
                    icon = Icons.Outlined.BatteryChargingFull,
                ) { on ->
                    settings.alertKeepAwake = on
                    keepAwake = on
                    if (on) requestBatteryExemption(context)
                    if (alerts) MonitorService.restart(context)
                }
            }

            Group(
                stringResource(R.string.group_privacy),
                footer = stringResource(R.string.privacy_footer) +
                    if (lockOn && alerts) " " + stringResource(R.string.privacy_footer_alerts) else "",
            ) {
                GroupSwitch(
                    stringResource(R.string.lock_app),
                    stringResource(R.string.lock_app_subtitle),
                    lockOn,
                    icon = Icons.Outlined.Lock,
                ) { on -> PinRequests.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
                if (lockOn) {
                    GroupRow(
                        stringResource(R.string.lock_change_pin),
                        stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                        icon = Icons.Outlined.Password,
                        showChevron = true,
                        onClick = { PinRequests.request = PinRequest.CHANGE },
                    )
                    GroupSwitch(
                        stringResource(R.string.lock_fingerprint),
                        stringResource(if (bioAvailable || vault.biometricEnabled) R.string.lock_fingerprint_settings_subtitle else R.string.lock_fingerprint_unavailable),
                        vault.biometricEnabled,
                        icon = Icons.Outlined.Fingerprint,
                        enabled = bioAvailable || vault.biometricEnabled,
                    ) { on ->
                        if (!on) {
                            vault.disableBiometric()
                        } else {
                            val activity = context.findFragmentActivity() ?: return@GroupSwitch
                            // A fresh key each time, bound to the fingerprints enrolled now.
                            val cipher = vault.biometricEnrolCipher() ?: run {
                                Toast.makeText(context, R.string.lock_fingerprint_setup_failed, Toast.LENGTH_SHORT).show()
                                return@GroupSwitch
                            }
                            showVaultPrompt(
                                activity,
                                context.getString(R.string.lock_fingerprint_enable_title),
                                cipher,
                                subtitle = context.getString(R.string.lock_fingerprint_enable_subtitle),
                            ) { unlocked ->
                                if (!vault.finishBiometricEnrol(unlocked)) {
                                    Toast.makeText(context, R.string.lock_fingerprint_setup_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    GroupChoice(
                        stringResource(R.string.lock_again),
                        LOCK_AGAIN.map { (v, label) -> v to stringResource(label) },
                        lockAfter,
                        icon = Icons.Outlined.Timer,
                        footer = stringResource(R.string.lock_again_footer),
                    ) { settings.lockAfterSeconds = it; lockAfter = it }
                }
                GroupSwitch(
                    stringResource(R.string.hide_screenshots),
                    stringResource(R.string.hide_screenshots_subtitle),
                    ScreenPrivacy.hideScreenshots,
                    icon = Icons.Outlined.VisibilityOff,
                ) { ScreenPrivacy.saveHideScreenshots(context, it) }
                GroupSwitch(
                    stringResource(R.string.hide_recents),
                    stringResource(R.string.hide_recents_subtitle),
                    ScreenPrivacy.hideRecents,
                    icon = Icons.Outlined.Layers,
                ) { ScreenPrivacy.saveHideRecents(context, it) }
            }

            Group(
                stringResource(R.string.live_stats),
                footer = stringResource(R.string.only_while_the_app_is_open),
            ) {
                GroupChoice(
                    stringResource(R.string.refresh_open_screens_every),
                    listOf(1, 2, 5, 10).map { it to pluralStringResource(R.plurals.x_seconds, it, it) },
                    liveRefresh,
                    icon = Icons.Outlined.Refresh,
                ) { settings.liveRefreshSeconds = it; liveRefresh = it }
            }
        }
    }
}

@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: android.content.Context) {
    val pm = context.getSystemService(PowerManager::class.java)
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    Lock.expectReturn()
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        )
    }
}
