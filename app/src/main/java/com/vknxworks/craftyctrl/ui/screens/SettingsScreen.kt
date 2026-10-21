@file:OptIn(ExperimentalMaterial3Api::class)

package com.vknxworks.craftyctrl.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vknxworks.craftyctrl.BuildConfig
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.data.CredentialStore
import com.vknxworks.craftyctrl.data.lock.Lock
import com.vknxworks.craftyctrl.data.lock.LockVault
import com.vknxworks.craftyctrl.monitor.MonitorService
import com.vknxworks.craftyctrl.ui.ScreenPrivacy
import com.vknxworks.craftyctrl.ui.lock.PinRequest
import com.vknxworks.craftyctrl.ui.lock.PinRequests
import com.vknxworks.craftyctrl.ui.lock.biometricAvailable
import com.vknxworks.craftyctrl.ui.lock.findFragmentActivity
import com.vknxworks.craftyctrl.ui.lock.showVaultPrompt
import com.vknxworks.craftyctrl.ui.settings.Group
import com.vknxworks.craftyctrl.ui.settings.GroupChoice
import com.vknxworks.craftyctrl.ui.settings.GroupRow
import com.vknxworks.craftyctrl.ui.settings.GroupSwitch
import com.vknxworks.craftyctrl.ui.theme.ThemeMode
import com.vknxworks.craftyctrl.ui.theme.ThemePrefs

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/CraftyCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/CraftyCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** When the app lock asks again after you leave the app (seconds; -1 = only when the app restarts). */
private val LOCK_AGAIN = listOf(
    0 to "As soon as you leave the app",
    60 to "After 1 minute away",
    300 to "After 5 minutes away",
    900 to "After 15 minutes away",
    3600 to "After 1 hour away",
    -1 to "Only when the app restarts",
)

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

    val bioAvailable = remember { biometricAvailable(context) }
    val uriHandler = LocalUriHandler.current
    fun open(url: String) {
        Lock.expectReturn()
        runCatching { uriHandler.openUri(url) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        alerts = settings.alertsEnabled
    }

    fun turnAlertsOn() {
        settings.alertsEnabled = true
        alerts = true
        // With the app lock on, alerts get their own copy of the login so they work while locked.
        store.syncBackgroundCopy(true)
        MonitorService.sync(context)
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifDenied = !granted
        if (granted) turnAlertsOn()
    }

    val scroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                scrollBehavior = scroll,
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
            Group("Appearance") {
                GroupChoice(
                    "Theme",
                    ThemeMode.entries.map { it to it.label },
                    ThemePrefs.mode,
                    icon = Icons.Outlined.DarkMode,
                ) { ThemePrefs.saveMode(context, it) }
                GroupSwitch(
                    "Use wallpaper colours",
                    "Replaces the default colours with ones from your wallpaper",
                    ThemePrefs.dynamicColor,
                    icon = Icons.Outlined.Palette,
                ) { ThemePrefs.saveDynamic(context, it) }
            }

            Group(
                "Notifications",
                footer = if (notifDenied) "Notifications are blocked for this app. Allow them in Android settings to get alerts." else null,
            ) {
                GroupSwitch(
                    "Server alerts",
                    "Get notified when a server stops or crashes, even with the app closed. Shows a quiet ongoing notification while on",
                    alerts,
                    icon = Icons.Outlined.NotificationsActive,
                ) { on ->
                    if (on) {
                        if (!MonitorService.canNotify(context)) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
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
                    "Check every",
                    listOf(15 to "15 seconds", 30 to "30 seconds", 60 to "1 minute", 300 to "5 minutes"),
                    interval,
                    icon = Icons.Outlined.Schedule,
                ) { settings.alertIntervalSeconds = it; interval = it }
                GroupSwitch(
                    "Keep checking while the screen is off",
                    "Without this, Android may pause checks while the phone sleeps, so alerts can arrive late. Uses more battery",
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
                "Privacy and security",
                footer = buildString {
                    append("CraftyCtrl has no analytics or tracking. It only talks to your own Crafty panel, ")
                    append("plus Mojang's player lookup and Crafty's server version list when you use those.")
                    if (lockOn && alerts) {
                        append(" Server alerts are on, so they keep their own copy of your Crafty login, encrypted with the ")
                        append("Android Keystore, to keep working while CraftyCtrl is locked. Turn off server alerts to keep it sealed with your PIN only.")
                    }
                },
            ) {
                GroupSwitch(
                    "App lock",
                    "Ask for an app PIN (and optionally your fingerprint) when opening CraftyCtrl. While locked, " +
                        "your Crafty login stays encrypted with the PIN.",
                    lockOn,
                    icon = Icons.Outlined.Lock,
                ) { on -> PinRequests.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
                if (lockOn) {
                    GroupRow(
                        "Change app PIN",
                        "At least ${LockVault.PIN_MIN} digits, separate from your phone's PIN",
                        icon = Icons.Outlined.Password,
                        showChevron = true,
                        onClick = { PinRequests.request = PinRequest.CHANGE },
                    )
                    GroupSwitch(
                        "Unlock with fingerprint",
                        if (bioAvailable || vault.biometricEnabled) {
                            "Only strong biometrics such as your fingerprint, checked by the phone's secure hardware. " +
                                "The PIN is still needed after a restart and every 72 hours."
                        } else {
                            "Set up a fingerprint on this phone first"
                        },
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
                                Toast.makeText(context, "Couldn't set up fingerprint unlock", Toast.LENGTH_SHORT).show()
                                return@GroupSwitch
                            }
                            showVaultPrompt(activity, "Turn on fingerprint unlock", cipher, subtitle = "Confirm your fingerprint") { unlocked ->
                                if (!vault.finishBiometricEnrol(unlocked)) {
                                    Toast.makeText(context, "Couldn't set up fingerprint unlock", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    GroupChoice(
                        "Lock again",
                        LOCK_AGAIN,
                        lockAfter,
                        icon = Icons.Outlined.Timer,
                        footer = "Picking a file, opening a link or the battery settings from CraftyCtrl doesn't count as leaving, " +
                            "unless you stay away for more than 5 minutes.",
                    ) { settings.lockAfterSeconds = it; lockAfter = it }
                }
                GroupSwitch(
                    "Hide in screenshots and recent apps",
                    "Blocks screenshots and screen recording, and blanks the app in recent apps. " +
                        "Sign-in, the file editor and server settings are always hidden",
                    ScreenPrivacy.hideContent,
                    icon = Icons.Outlined.VisibilityOff,
                ) { ScreenPrivacy.saveHideContent(context, it) }
            }

            Group(
                "Live stats",
                footer = "Only while the app is open. Crafty itself recalculates server stats on its own timer " +
                    "(\"status_update_frequency_seconds\" in Crafty's config.json), so numbers can't change faster than that.",
            ) {
                GroupChoice(
                    "Refresh open screens every",
                    listOf(1 to "1 second", 2 to "2 seconds", 5 to "5 seconds", 10 to "10 seconds"),
                    liveRefresh,
                    icon = Icons.Outlined.Refresh,
                ) { settings.liveRefreshSeconds = it; liveRefresh = it }
            }

            Group(stringResource(R.string.group_about)) {
                GroupRow(
                    stringResource(R.string.support_title),
                    stringResource(R.string.support_subtitle),
                    icon = Icons.Filled.Favorite,
                    showChevron = true,
                    onClick = { open(SPONSOR_URL) },
                )
                GroupRow(
                    stringResource(R.string.bugs_title),
                    stringResource(R.string.bugs_subtitle),
                    icon = Icons.Filled.BugReport,
                    showChevron = true,
                    onClick = { open(ISSUES_URL) },
                )
                GroupRow(
                    stringResource(R.string.source_title),
                    stringResource(R.string.source_subtitle),
                    icon = Icons.Filled.Code,
                    showChevron = true,
                    onClick = { open(REPO_URL) },
                )
                GroupRow(
                    stringResource(R.string.licence_title),
                    stringResource(R.string.licence_subtitle),
                    icon = Icons.Filled.Gavel,
                    showChevron = true,
                    onClick = { open(LICENCE_URL) },
                )
            }
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
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
