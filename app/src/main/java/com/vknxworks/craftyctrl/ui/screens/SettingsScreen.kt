@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vknxworks.craftyctrl.ui.screens

import com.vknxworks.craftyctrl.ui.lock.findFragmentActivity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.BatteryChargingFull
import com.vknxworks.craftyctrl.ui.ItemDivider
import com.vknxworks.craftyctrl.ui.theme.ThemePrefs
import com.vknxworks.craftyctrl.ui.theme.ThemeMode
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.monitor.MonitorService
import com.vknxworks.craftyctrl.ui.SectionLabel
import com.vknxworks.craftyctrl.ui.lock.PIN_MAX
import com.vknxworks.craftyctrl.ui.lock.PIN_MIN
import com.vknxworks.craftyctrl.ui.lock.biometricAvailable
import com.vknxworks.craftyctrl.ui.lock.showBiometricPrompt
import com.vknxworks.craftyctrl.ui.lock.resetBiometricKey
import com.vknxworks.craftyctrl.ui.ScreenPrivacy
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.ui.text.style.TextAlign
import com.vknxworks.craftyctrl.BuildConfig
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.vknxworks.craftyctrl.R

private enum class PinDialog { NEW, CHANGE, DISABLE }

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/CraftyCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/CraftyCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }

    var pinSet by remember { mutableStateOf(settings.pinSet) }
    var bio by remember { mutableStateOf(settings.biometricEnabled) }
    var lockAfter by remember { mutableIntStateOf(settings.lockAfterSeconds) }
    var pinDialog by remember { mutableStateOf<PinDialog?>(null) }

    var liveRefresh by remember { mutableIntStateOf(settings.liveRefreshSeconds) }
    var alerts by remember { mutableStateOf(settings.alertsEnabled) }
    var interval by remember { mutableIntStateOf(settings.alertIntervalSeconds) }
    var keepAwake by remember { mutableStateOf(settings.alertKeepAwake) }
    var notifDenied by remember { mutableStateOf(false) }

    val bioAvailable = remember { biometricAvailable(context) }
    val uriHandler = LocalUriHandler.current
    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        alerts = settings.alertsEnabled
        pinSet = settings.pinSet
        bio = settings.biometricEnabled
    }

    fun turnAlertsOn() {
        settings.alertsEnabled = true
        alerts = true
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ------------------------------------------------ app lock
            SectionLabel("App lock")
            SettingsCard {
                SwitchRow(
                    title = "Require a PIN",
                    icon = Icons.Outlined.Lock,
                    body = "Ask for a PIN when opening the app.",
                    checked = pinSet,
                    onChange = { on -> pinDialog = if (on) PinDialog.NEW else PinDialog.DISABLE },
                )
                if (pinSet) {
                    ItemDivider()
                    SwitchRow(
                        title = "Unlock with fingerprint or face",
                        icon = Icons.Outlined.Fingerprint,
                        body = if (bioAvailable) "Your PIN still works as a backup." else "No fingerprint or face unlock is set up on this phone.",
                        checked = bio,
                        enabled = bioAvailable,
                        onChange = { on ->
                            val activity = context.findFragmentActivity()
                            if (on && activity != null) {
                                // A fresh key each time, bound to the fingerprints and faces enrolled now.
                                resetBiometricKey()
                                showBiometricPrompt(activity, "Confirm to turn on") {
                                    settings.biometricEnabled = true
                                    bio = true
                                }
                            } else {
                                settings.biometricEnabled = false
                                bio = false
                                resetBiometricKey()
                            }
                        },
                    )
                    ItemDivider()
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lock again after leaving the app", style = MaterialTheme.typography.bodyLarge)
                        ChoiceChips(
                            options = listOf(0 to "Immediately", 30 to "30 s", 60 to "1 min", 300 to "5 min"),
                            selected = lockAfter,
                            onSelect = { settings.lockAfterSeconds = it; lockAfter = it },
                        )
                    }
                    ItemDivider()
                    TextButton(onClick = { pinDialog = PinDialog.CHANGE }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Change PIN")
                    }
                }
            }

            SectionLabel("Privacy")
            SettingsCard {
                SwitchRow(
                    title = "Hide content in screenshots & recents",
                    icon = Icons.Outlined.VisibilityOff,
                    body = "Blocks screenshots and screen recording, and blanks the app in Recents. " +
                        "Sign-in, the file editor and server settings are always hidden.",
                    checked = ScreenPrivacy.hideContent,
                    onChange = { ScreenPrivacy.saveHideContent(context, it) },
                )
            }

            // ------------------------------------------------ alerts
            SectionLabel("Appearance")
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = ThemePrefs.mode == m,
                                onClick = { ThemePrefs.saveMode(context, m) },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(m.label, maxLines = 1) }
                        }
                    }
                }
                ItemDivider()
                SwitchRow(
                    title = "Material You colours",
                    icon = Icons.Outlined.Palette,
                    body = "Match the app to your wallpaper",
                    checked = ThemePrefs.dynamicColor,
                    onChange = { ThemePrefs.saveDynamic(context, it) },
                )
                ItemDivider()
                SwitchRow(
                    title = "Pure black",
                    icon = Icons.Outlined.DarkMode,
                    body = "True black backgrounds in dark mode",
                    checked = ThemePrefs.pureBlack,
                    onChange = { ThemePrefs.savePureBlack(context, it) },
                )
            }

            SectionLabel("Live stats")
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Refresh open screens every", style = MaterialTheme.typography.bodyLarge)
                    ChoiceChips(
                        options = listOf(1 to "1 s", 2 to "2 s", 5 to "5 s", 10 to "10 s"),
                        selected = liveRefresh,
                        onSelect = { settings.liveRefreshSeconds = it; liveRefresh = it },
                    )
                    Text(
                        "Only while the app is open. Crafty itself recalculates server stats on its own timer " +
                            "(\"status_update_frequency_seconds\" in Crafty's config.json), so numbers can't change faster than that.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionLabel("Alerts")
            SettingsCard {
                SwitchRow(
                    title = "Server alerts",
                    icon = Icons.Outlined.NotificationsActive,
                    body = "Get notified when a server stops or crashes, even with the app closed. Shows a quiet ongoing notification while on.",
                    checked = alerts,
                    onChange = { on ->
                        if (on) {
                            if (!MonitorService.canNotify(context)) {
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                turnAlertsOn()
                            }
                        } else {
                            settings.alertsEnabled = false
                            alerts = false
                            MonitorService.sync(context)
                        }
                    },
                )
                if (notifDenied) {
                    Text(
                        "Notifications are blocked for this app. Allow them in Android settings to get alerts.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                ItemDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Check every", style = MaterialTheme.typography.bodyLarge)
                    ChoiceChips(
                        options = listOf(15 to "15 s", 30 to "30 s", 60 to "1 min", 300 to "5 min"),
                        selected = interval,
                        onSelect = { settings.alertIntervalSeconds = it; interval = it },
                    )
                }
                ItemDivider()
                SwitchRow(
                    title = "Keep checking while the screen is off",
                    icon = Icons.Outlined.BatteryChargingFull,
                    body = "Without this, Android may pause checks while the phone sleeps, so alerts can arrive late. Uses more battery.",
                    checked = keepAwake,
                    onChange = { on ->
                        settings.alertKeepAwake = on
                        keepAwake = on
                        if (on) requestBatteryExemption(context)
                        if (alerts) MonitorService.restart(context)
                    },
                )
            }

            SectionLabel(stringResource(R.string.group_about))
            SettingsCard {
                LinkRow(
                    title = stringResource(R.string.support_title),
                    body = stringResource(R.string.support_subtitle),
                    icon = Icons.Filled.Favorite,
                    onClick = { open(SPONSOR_URL) },
                )
                ItemDivider()
                LinkRow(
                    title = stringResource(R.string.bugs_title),
                    body = stringResource(R.string.bugs_subtitle),
                    icon = Icons.Filled.BugReport,
                    onClick = { open(ISSUES_URL) },
                )
                ItemDivider()
                LinkRow(
                    title = stringResource(R.string.source_title),
                    body = stringResource(R.string.source_subtitle),
                    icon = Icons.Filled.Code,
                    onClick = { open(REPO_URL) },
                )
                ItemDivider()
                LinkRow(
                    title = stringResource(R.string.licence_title),
                    body = stringResource(R.string.licence_subtitle),
                    icon = Icons.Filled.Gavel,
                    onClick = { open(LICENCE_URL) },
                )
            }
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    pinDialog?.let { mode ->
        PinSetupDialog(
            mode = mode,
            settings = settings,
            onDismiss = { pinDialog = null },
            onDone = {
                pinSet = settings.pinSet
                bio = settings.biometricEnabled
                pinDialog = null
            },
        )
    }
}

@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: android.content.Context) {
    val pm = context.getSystemService(PowerManager::class.java)
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        )
    }
}

@Composable
private fun PinSetupDialog(
    mode: PinDialog,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val needsCurrent = mode != PinDialog.NEW
    val needsNew = mode != PinDialog.DISABLE
    var current by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun digits(v: String) = v.filter { it.isDigit() }.take(PIN_MAX)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (mode) {
                    PinDialog.NEW -> "Set a PIN"
                    PinDialog.CHANGE -> "Change PIN"
                    PinDialog.DISABLE -> "Turn off app lock"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (needsCurrent) PinField("Current PIN", current) { current = digits(it) }
                if (needsNew) {
                    PinField("New PIN ($PIN_MIN–$PIN_MAX digits)", first) { first = digits(it) }
                    PinField("Repeat new PIN", second) { second = digits(it) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    scope.launch {
                        working = true
                        // PBKDF2 is deliberately slow; keep it off the main thread.
                        val currentOk = !needsCurrent || withContext(Dispatchers.Default) { settings.checkPin(current) }
                        error = when {
                            !currentOk -> "Current PIN is wrong"
                            needsNew && first.length < PIN_MIN -> "Use at least $PIN_MIN digits"
                            needsNew && first != second -> "The PINs don't match"
                            else -> null
                        }
                        if (error == null) {
                            withContext(Dispatchers.Default) {
                                if (needsNew) settings.setPin(first) else settings.clearPin()
                            }
                            onDone()
                        }
                        working = false
                    }
                },
            ) { Text(if (mode == PinDialog.DISABLE) "Turn off" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    androidx.compose.material3.Card(
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@Composable
private fun SwitchRow(
    title: String,
    body: String?,
    checked: Boolean,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    androidx.compose.material3.ListItem(
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        headlineContent = { Text(title) },
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
    )
}

@Composable
private fun LinkRow(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    androidx.compose.material3.ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ChoiceChips(options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}
