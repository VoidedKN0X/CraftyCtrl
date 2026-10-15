@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vknxworks.craftyremote.ui.screens

import com.vknxworks.craftyremote.ui.theme.Accent
import com.vknxworks.craftyremote.ui.theme.ThemePrefs
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
import com.vknxworks.craftyremote.data.AppSettings
import com.vknxworks.craftyremote.monitor.MonitorService
import com.vknxworks.craftyremote.ui.SectionLabel
import com.vknxworks.craftyremote.ui.lock.PIN_MAX
import com.vknxworks.craftyremote.ui.lock.PIN_MIN
import com.vknxworks.craftyremote.ui.lock.biometricAvailable
import com.vknxworks.craftyremote.ui.lock.showBiometricPrompt

private enum class PinDialog { NEW, CHANGE, DISABLE }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
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
                    body = "Ask for a PIN when opening the app.",
                    checked = pinSet,
                    onChange = { on -> pinDialog = if (on) PinDialog.NEW else PinDialog.DISABLE },
                )
                if (pinSet) {
                    HorizontalDivider()
                    SwitchRow(
                        title = "Unlock with fingerprint or face",
                        body = if (bioAvailable) "Your PIN still works as a backup." else "No fingerprint or face unlock is set up on this phone.",
                        checked = bio,
                        enabled = bioAvailable,
                        onChange = { on ->
                            val activity = context as? FragmentActivity
                            if (on && activity != null) {
                                showBiometricPrompt(activity, "Confirm to turn on") {
                                    settings.biometricEnabled = true
                                    bio = true
                                }
                            } else {
                                settings.biometricEnabled = false
                                bio = false
                            }
                        },
                    )
                    HorizontalDivider()
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lock again after leaving the app", style = MaterialTheme.typography.bodyLarge)
                        ChoiceChips(
                            options = listOf(0 to "Immediately", 30 to "30 s", 60 to "1 min", 300 to "5 min"),
                            selected = lockAfter,
                            onSelect = { settings.lockAfterSeconds = it; lockAfter = it },
                        )
                    }
                    HorizontalDivider()
                    TextButton(onClick = { pinDialog = PinDialog.CHANGE }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Change PIN")
                    }
                }
            }

            // ------------------------------------------------ alerts
            SectionLabel("Appearance")
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Accent colour", style = MaterialTheme.typography.bodyLarge)
                    val accents = Accent.entries.filter { it != Accent.WALLPAPER || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
                    ChoiceChips(
                        options = accents.map { it.ordinal to it.label },
                        selected = ThemePrefs.accent.ordinal,
                        onSelect = { ThemePrefs.save(context, Accent.entries[it]) },
                    )
                    Text(
                        "Used for running servers and the players chart. Wallpaper follows your phone's Material You colour.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    body = "Get notified when a server stops or crashes, even with the app closed. Shows a quiet ongoing notification while on.",
                    checked = alerts,
                    onChange = { on ->
                        if (on) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !MonitorService.canNotify(context)) {
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
                HorizontalDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Check every", style = MaterialTheme.typography.bodyLarge)
                    ChoiceChips(
                        options = listOf(15 to "15 s", 30 to "30 s", 60 to "1 min", 300 to "5 min"),
                        selected = interval,
                        onSelect = { settings.alertIntervalSeconds = it; interval = it },
                    )
                }
                HorizontalDivider()
                SwitchRow(
                    title = "Keep checking while the screen is off",
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
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    body: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            body?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}
