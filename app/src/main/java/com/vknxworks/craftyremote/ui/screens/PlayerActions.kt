@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vknxworks.craftyremote.ui.screens

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
import com.vknxworks.craftyremote.ui.SectionLabel

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
                Text("Manage a player", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Player name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionLabel("Message")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text("Whisper to this player") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = ready && message.isNotBlank(),
                    onClick = { run("tell $t ${oneLine(message)}", "Message sent to $name") },
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Send")
                }
            }

            SectionLabel("Game mode")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("survival", "creative", "adventure", "spectator").forEach { mode ->
                    FilterChip(
                        selected = false,
                        enabled = ready,
                        onClick = { run("gamemode $mode $t", "$name is now in $mode") },
                        label = { Text(mode.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            SectionLabel("Permissions")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Make operator", Icons.Filled.Shield, ready) { run("op $t", "$name is now an operator") }
                ActionChip("Remove operator", Icons.Filled.RemoveModerator, ready) { run("deop $t", "$name is no longer an operator") }
                val listCmd = if (isBedrock) "allowlist" else "whitelist"
                ActionChip("Add to $listCmd", Icons.Filled.PersonAdd, ready) { run("$listCmd add $t", "$name added to the $listCmd") }
            }

            SectionLabel("Moderation")
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                placeholder = { Text("Reason (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Kick", Icons.AutoMirrored.Filled.ExitToApp, ready) {
                    confirm = Confirm("Kick $name?", "They can rejoin straight away.", "kick $t $why".trim(), "Kicked $name")
                }
                if (!isBedrock) {
                    ActionChip("Ban", Icons.Filled.Block, ready) {
                        confirm = Confirm("Ban $name?", "They won't be able to join until you pardon them from the console.", "ban $t $why".trim(), "Banned $name")
                    }
                }
                ActionChip("Clear inventory", Icons.Filled.DeleteSweep, ready) {
                    confirm = Confirm("Clear $name's inventory?", "Everything they're carrying is deleted. This can't be undone.", "clear $t", "Cleared $name's inventory")
                }
                ActionChip("Kill", Icons.Filled.HeartBroken, ready) {
                    confirm = Confirm("Kill $name?", "They'll respawn and may drop their items.", "kill $t", "Killed $name")
                }
            }
            if (isBedrock) {
                Text(
                    "Bedrock servers have no ban command. Remove players from the allowlist instead.",
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
                    Text("Confirm", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
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
