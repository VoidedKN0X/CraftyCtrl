@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vknxworks.craftyctrl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vknxworks.craftyctrl.ui.SectionLabel
import androidx.compose.ui.res.stringResource
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str

/** Minecraft names: letters, digits, underscores and (Bedrock) spaces. Anything else is stripped. */
private fun cleanName(raw: String) = raw.filter { it.isLetterOrDigit() || it == '_' || it == ' ' }.trim().take(32)

/** Bedrock gamertags may contain spaces, which commands need quoted. */
private fun target(name: String) = if (' ' in name) "\"$name\"" else name

/** Strips line breaks so free text can't smuggle in a second console command. */
private fun oneLine(text: String) = text.replace(Regex("[\\r\\n]+"), " ").trim()

private data class Confirm(val title: String, val body: String, val command: String, val done: String)

/**
 * Actions for one player. [fixedName] is null when opened from "Manage a player…",
 * where the name is typed in (Bedrock doesn't report names to Crafty).
 */
@Composable
fun PlayerSheet(
    fixedName: String?,
    isBedrock: Boolean,
    onDismiss: () -> Unit,
    onCommand: (command: String, done: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var typed by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    val name = cleanName(fixedName ?: typed)
    val ready = name.isNotEmpty()
    val t = target(name)
    val why = oneLine(reason)

    fun run(command: String, done: String) {
        onCommand(command, done)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (fixedName != null) {
                Text(fixedName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            } else {
                Text(stringResource(R.string.manage_a_player), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.player_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionLabel(stringResource(R.string.message))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text(stringResource(R.string.whisper_to_this_player)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = ready && message.isNotBlank(),
                    onClick = { run("tell $t ${oneLine(message)}", str(R.string.message_sent_to_x, name)) },
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.send))
                }
            }

            SectionLabel(stringResource(R.string.game_mode))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("survival", "creative", "adventure", "spectator").forEach { mode ->
                    FilterChip(
                        selected = false,
                        enabled = ready,
                        onClick = { run("gamemode $mode $t", str(R.string.x_is_now_in_x, name, mode)) },
                        label = { Text(mode.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            SectionLabel(stringResource(R.string.permissions))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(stringResource(R.string.make_operator), Icons.Filled.Shield, ready) { run("op $t", stringResource(R.string.x_is_now_an_operator, name)) }
                ActionChip(stringResource(R.string.remove_operator), Icons.Filled.RemoveModerator, ready) { run("deop $t", stringResource(R.string.x_is_no_longer_an_operator, name)) }
                val listCmd = if (isBedrock) "allowlist" else "whitelist"
                ActionChip(stringResource(R.string.add_to_x, listCmd), Icons.Filled.PersonAdd, ready) { run("$listCmd add $t", stringResource(R.string.x_added_to_the_x, name, listCmd)) }
            }

            SectionLabel(stringResource(R.string.moderation))
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                placeholder = { Text(stringResource(R.string.reason_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(stringResource(R.string.kick), Icons.AutoMirrored.Filled.ExitToApp, ready) {
                    confirm = Confirm(stringResource(R.string.kick_x, name), stringResource(R.string.they_can_rejoin_straight_away), "kick $t $why".trim(), stringResource(R.string.kicked_x, name))
                }
                if (!isBedrock) {
                    ActionChip(stringResource(R.string.ban), Icons.Filled.Block, ready) {
                        confirm = Confirm(stringResource(R.string.ban_x, name), stringResource(R.string.they_won_t_be_able_to), "ban $t $why".trim(), stringResource(R.string.banned_x, name))
                    }
                }
                ActionChip(stringResource(R.string.clear_inventory), Icons.Filled.DeleteSweep, ready) {
                    confirm = Confirm(stringResource(R.string.clear_x_s_inventory, name), stringResource(R.string.everything_they_re_carrying_is_deleted), "clear $t", stringResource(R.string.cleared_x_s_inventory, name))
                }
                ActionChip(stringResource(R.string.power_kill), Icons.Filled.HeartBroken, ready) {
                    confirm = Confirm(stringResource(R.string.kill_x, name), stringResource(R.string.they_ll_respawn_and_may_drop), "kill $t", stringResource(R.string.killed_x, name))
                }
            }
            if (isBedrock) {
                Text(
                    stringResource(R.string.bedrock_servers_have_no_ban_command),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.body) },
            confirmButton = {
                TextButton(onClick = { confirm = null; run(c.command, c.done) }) {
                    Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}
