@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vknxworks.craftyctrl.ui.screens

import com.vknxworks.craftyctrl.ui.theme.Mono
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.craftyctrl.data.BackupConfig
import com.vknxworks.craftyctrl.data.ScheduledTask
import com.vknxworks.craftyctrl.data.TaskAction
import com.vknxworks.craftyctrl.ui.SectionLabel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.annotation.StringRes
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str
import androidx.compose.ui.res.stringResource

private enum class Timing(@StringRes private val labelRes: Int) {
    INTERVAL(R.string.timing_repeat), DAILY(R.string.timing_daily), CRON(R.string.timing_cron);

    val label: String get() = str(labelRes)
}

@Composable
private fun unitLabel(u: String): String = when (u) {
    "minutes" -> stringResource(R.string.unit_minutes)
    "hours" -> stringResource(R.string.unit_hours)
    "days" -> stringResource(R.string.unit_days)
    else -> u
}

private val timeRegex = Regex("""^([01]?\d|2[0-3]):([0-5]\d)$""")
// Five space-separated cron fields of digits and * , - /
private val cronRegex = Regex("""^\s*[\d*,/\-]+(\s+[\d*,/\-]+){4}\s*$""")

/**
 * Create or edit a scheduled task. [existing] null means a new task; [preset] fixes the action
 * (e.g. a backup schedule added from a backup profile).
 */
@Composable
fun TaskEditorSheet(
    existing: ScheduledTask?,
    backups: List<BackupConfig>,
    saving: Boolean,
    preset: Pair<TaskAction, String?>? = null,
    onDismiss: () -> Unit,
    onSave: (JsonObject) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var action by remember { mutableStateOf(existing?.kind ?: preset?.first ?: TaskAction.BACKUP) }
    var actionId by remember { mutableStateOf(existing?.actionId ?: preset?.second) }
    var command by remember { mutableStateOf(existing?.command.orEmpty()) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var oneTime by remember { mutableStateOf(existing?.oneTime ?: false) }
    var delay by remember { mutableStateOf((existing?.delay ?: 0).toString()) }

    val initialTiming = when {
        existing?.cronString != null -> Timing.CRON
        existing?.intervalType == "days" && existing?.startTime != null -> Timing.DAILY
        else -> if (existing == null) Timing.DAILY else Timing.INTERVAL
    }
    var timing by remember { mutableStateOf(initialTiming) }
    var every by remember { mutableStateOf((existing?.interval ?: 1).toString()) }
    var unit by remember { mutableStateOf(existing?.intervalType?.takeIf { it in listOf("minutes", "hours", "days") } ?: "hours") }
    var at by remember { mutableStateOf(existing?.startTime ?: "03:00") }
    var cron by remember { mutableStateOf(existing?.cronString ?: "0 3 * * *") }
    var confirmDelete by remember { mutableStateOf(false) }

    val unsupported = existing != null && (existing.kind == null || existing.intervalType == "reaction")

    val error: String? = when {
        unsupported -> stringResource(R.string.this_task_type_can_only_be)
        action == TaskAction.COMMAND && command.isBlank() -> stringResource(R.string.enter_the_command_to_run)
        timing == Timing.INTERVAL && (every.toIntOrNull() ?: 0) < 1 -> stringResource(R.string.repeat_interval_must_be_at_least)
        timing == Timing.DAILY && !timeRegex.matches(at.trim()) -> "Use 24-hour time, e.g. 03:00"
        timing == Timing.CRON && !cronRegex.matches(cron) -> "Cron needs 5 fields, e.g. 0 3 * * *"
        (delay.toIntOrNull() ?: -1) < 0 -> stringResource(R.string.delay_must_be_0_or_more)
        else -> null
    }

    fun build(): JsonObject = buildJsonObject {
        put("name", name.trim().ifEmpty { action.label })
        put("enabled", enabled)
        put("action", action.api)
        // The backup config to run; other actions don't take one.
        if (action == TaskAction.BACKUP) actionId?.let { put("action_id", it) }
        put("command", if (action == TaskAction.COMMAND) command.trim().removePrefix("/") else "")
        put("one_time", oneTime)
        put("delay", delay.toIntOrNull() ?: 0)
        when (timing) {
            Timing.INTERVAL -> {
                put("interval", every.toInt())
                put("interval_type", unit)
                put("cron_string", "")
            }
            Timing.DAILY -> {
                put("interval", 1)
                put("interval_type", "days")
                put("start_time", at.trim().padStart(5, '0'))
                put("cron_string", "")
            }
            Timing.CRON -> {
                put("interval_type", "")
                put("cron_string", cron.trim().replace(Regex("\\s+"), " "))
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (existing == null) stringResource(R.string.new_schedule) else stringResource(R.string.edit_schedule),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(64) },
                label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(stringResource(R.string.action))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskAction.entries.forEach { a ->
                    FilterChip(
                        selected = action == a,
                        onClick = { action = a },
                        enabled = preset == null,
                        label = { Text(a.label) },
                    )
                }
            }
            when (action) {
                TaskAction.COMMAND -> OutlinedTextField(
                    value = command, onValueChange = { command = it.replace("\n", " ") },
                    label = { Text(stringResource(R.string.command_without)) }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
                    modifier = Modifier.fillMaxWidth(),
                )
                TaskAction.BACKUP -> if (backups.size > 1) {
                    Text(stringResource(R.string.backup_profile), style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        backups.forEach { b ->
                            FilterChip(
                                selected = actionId == b.id || (actionId == null && b.isDefault),
                                onClick = { actionId = b.id },
                                label = { Text(b.name) },
                            )
                        }
                    }
                }
                else -> Unit
            }

            SectionLabel(stringResource(R.string.when))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Timing.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = timing == t,
                        onClick = { timing = t },
                        shape = SegmentedButtonDefaults.itemShape(i, Timing.entries.size),
                    ) { Text(t.label) }
                }
            }
            when (timing) {
                Timing.INTERVAL -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.every), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = every, onValueChange = { v -> every = v.filter { it.isDigit() }.take(4) },
                        singleLine = true, modifier = Modifier.width(88.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.width(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("minutes", "hours", "days").forEach { u ->
                            FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(unitLabel(u)) })
                        }
                    }
                }
                Timing.DAILY -> OutlinedTextField(
                    value = at, onValueChange = { at = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                    label = { Text(stringResource(R.string.time_24_hour_server_s_clock)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Timing.CRON -> OutlinedTextField(
                    value = cron, onValueChange = { cron = it.take(64) },
                    label = { Text(stringResource(R.string.cron_minute_hour_day_month_weekday)) }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
                    supportingText = { Text(stringResource(R.string.example_0_3_runs_at_03)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = delay, onValueChange = { v -> delay = v.filter { it.isDigit() }.take(5) },
                label = { Text(stringResource(R.string.delay_before_running_seconds)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow(stringResource(R.string.enabled), enabled) { enabled = it }
            ToggleRow(stringResource(R.string.run_once_then_delete), oneTime) { oneTime = it }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDelete != null) {
                    OutlinedButton(onClick = { confirmDelete = true }, enabled = !saving) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onSave(build()) }, enabled = error == null && !saving) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save))
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_this_schedule)) },
            text = { Text(stringResource(R.string.x_will_stop_running_this_can, existing?.name)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Edit a backup profile's settings (location and exclusions stay in the web panel). */
@Composable
fun BackupEditorSheet(
    config: BackupConfig,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (JsonObject) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(config.name) }
    var keep by remember { mutableStateOf(config.maxBackups?.toString() ?: "0") }
    var compress by remember { mutableStateOf(config.compress) }
    var shutdown by remember { mutableStateOf(config.shutdown) }
    var before by remember { mutableStateOf(config.before) }
    var after by remember { mutableStateOf(config.after) }

    val error = when {
        name.trim().length < 3 -> stringResource(R.string.name_needs_at_least_3_characters)
        keep.toIntOrNull() == null -> stringResource(R.string.enter_how_many_backups_to_keep)
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.backup_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(64) },
                label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = keep, onValueChange = { v -> keep = v.filter { it.isDigit() }.take(4) },
                label = { Text(stringResource(R.string.backups_to_keep)) },
                supportingText = { Text(stringResource(R.string.older_backups_are_deleted_beyond_this)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow(stringResource(R.string.compress_backups), compress) { compress = it }
            ToggleRow(stringResource(R.string.stop_the_server_while_backing_up), shutdown) { shutdown = it }
            OutlinedTextField(
                value = before, onValueChange = { before = it.replace("\n", " ") },
                label = { Text(stringResource(R.string.console_command_before_backup)) }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = after, onValueChange = { after = it.replace("\n", " ") },
                label = { Text(stringResource(R.string.console_command_after_backup)) }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.backup_location_and_excluded_folders_can),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    enabled = error == null && !saving,
                    onClick = {
                        onSave(buildJsonObject {
                            put("backup_name", name.trim())
                            put("max_backups", keep.toInt())
                            put("compress", compress)
                            put("shutdown", shutdown)
                            put("before", before.trim().removePrefix("/"))
                            put("after", after.trim().removePrefix("/"))
                        })
                    },
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
