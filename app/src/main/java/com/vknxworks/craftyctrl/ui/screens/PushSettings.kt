package com.vknxworks.craftyctrl.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.ServerSummary
import com.vknxworks.craftyctrl.data.lock.Lock
import com.vknxworks.craftyctrl.push.Push
import com.vknxworks.craftyctrl.push.PushStatus
import com.vknxworks.craftyctrl.push.UnifiedPush
import com.vknxworks.craftyctrl.ui.components.CtrlButton
import com.vknxworks.craftyctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.craftyctrl.ui.rememberSensitiveClipboard
import com.vknxworks.craftyctrl.ui.settings.Group
import com.vknxworks.craftyctrl.ui.settings.GroupRow
import com.vknxworks.craftyctrl.ui.settings.GroupSwitch
import com.vknxworks.craftyctrl.ui.theme.ConsoleBackground
import com.vknxworks.craftyctrl.ui.theme.ConsoleText
import com.vknxworks.craftyctrl.ui.theme.CtrlColors
import com.vknxworks.craftyctrl.ui.theme.CtrlType
import kotlinx.coroutines.launch

/**
 * Settings > Push from Crafty: push through ntfy (UnifiedPush) only. Shows the push address, sets up Crafty's
 * webhooks through the API (or shows what to enter in Crafty's Webhooks tab by hand), and warns when ntfy is
 * missing or battery-restricted.
 */
@Composable
fun PushSettings(api: CraftyApi, push: Boolean, onPushChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val status by Push.status.collectAsStateWithLifecycle()
    var resumes by remember { mutableIntStateOf(0) }
    val ntfyInstalled = remember(resumes) { UnifiedPush.isNtfyInstalled(context) }
    val ntfyUnrestricted = remember(resumes) { UnifiedPush.isNtfyUnrestricted(context) }
    val clipboard = rememberSensitiveClipboard()
    val scope = rememberCoroutineScope()
    var setupBusy by remember { mutableStateOf(false) }
    var setupResult by remember { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resumes++
        Push.load(context, AppSettings(context).pushEnabled)
    }

    // The push address holds the secret ntfy topic: keep it out of clipboard previews.
    fun copy(text: String) {
        clipboard.copy(text)
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    Group(
        stringResource(R.string.group_push),
        footer = stringResource(R.string.push_footer),
    ) {
        GroupSwitch(
            stringResource(R.string.push_title),
            stringResource(R.string.push_subtitle),
            push && ntfyInstalled,
            icon = Icons.Outlined.CloudSync,
            enabled = ntfyInstalled || push,
        ) { on -> onPushChange(on) }

        if (!ntfyInstalled) {
            PushNote(stringResource(R.string.push_no_ntfy)) {
                TextButton(onClick = {
                    Lock.expectReturn()
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UnifiedPush.NTFY_DOWNLOAD)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text(stringResource(R.string.push_install_ntfy), color = CtrlColors.AccentText) }
            }
        }

        if (push && ntfyInstalled) {
            when (val st = status) {
                PushStatus.NoNtfy, PushStatus.Off, PushStatus.Registering -> PushNote(stringResource(R.string.push_registering)) {
                    TextButton(onClick = { Push.register(context) }) { Text(stringResource(R.string.retry), color = CtrlColors.AccentText) }
                }
                is PushStatus.Failed -> PushNote(stringResource(R.string.push_failed, st.reason)) {
                    TextButton(onClick = { Push.register(context) }) { Text(stringResource(R.string.retry), color = CtrlColors.AccentText) }
                }
                is PushStatus.Unavailable -> PushNote(stringResource(R.string.push_unavailable)) {}
                is PushStatus.Registered -> {
                    GroupRow(
                        stringResource(R.string.push_endpoint),
                        st.endpoint,
                        icon = Icons.Outlined.Link,
                        onClick = { copy(st.endpoint) },
                        trailing = { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy), tint = CtrlColors.Muted) },
                    )
                    PushNote(stringResource(R.string.push_endpoint_footer)) {}
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(stringResource(R.string.push_setup_title), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.push_setup_body), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                        Spacer(Modifier.height(10.dp))
                        CtrlButton(
                            onClick = {
                                if (!setupBusy) {
                                setupBusy = true
                                setupResult = null
                                scope.launch {
                                    setupResult = try {
                                        val r = Push.setUpWebhooks(api, st.endpoint)
                                        when {
                                            r.total == 0 -> context.getString(R.string.push_setup_no_servers)
                                            r.done == r.total -> context.getString(R.string.push_setup_done, r.done)
                                            else -> context.getString(R.string.push_setup_partly, r.done, r.total, r.error ?: "")
                                        }
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        context.getString(R.string.push_setup_failed, e.message ?: "")
                                    } finally {
                                        setupBusy = false
                                    }
                                }
                                }
                            },
                            enabled = !setupBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (setupBusy) {
                                CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp, color = CtrlColors.OnAccent)
                            } else {
                                Text(stringResource(R.string.push_setup_button))
                            }
                        }
                        setupResult?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.push_manual_title), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.push_manual_body), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                        Spacer(Modifier.height(8.dp))
                        val manual = stringResource(
                            R.string.push_manual_fields,
                            Push.WEBHOOK_NAME,
                            st.endpoint,
                            Push.BODY_TEMPLATE,
                        )
                        Text(
                            manual,
                            style = CtrlType.MonoSmall,
                            color = ConsoleText,
                            softWrap = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ConsoleBackground)
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CtrlSecondaryButton(onClick = { copy(Push.BODY_TEMPLATE) }) {
                                Icon(Icons.Outlined.ContentCopy, null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.push_copy_message))
                            }
                        }
                    }
                }
            }
        }

        if (push && ntfyInstalled && !ntfyUnrestricted) {
            GroupRow(
                stringResource(R.string.battery_ntfy_restricted),
                stringResource(R.string.battery_ntfy_restricted_body),
                icon = Icons.Outlined.BatteryAlert,
                iconTint = MaterialTheme.colorScheme.error,
                onClick = {
                    Lock.expectReturn()
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${UnifiedPush.NTFY}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
    }
}

/** Settings > Player alerts: a switch per server for join and leave notifications. */
@Composable
fun PlayerAlertSettings(api: CraftyApi, alertsOn: Boolean) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var selected by remember { mutableStateOf(settings.playerAlertServers) }
    var servers by remember { mutableStateOf<List<ServerSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(api) {
        try {
            servers = api.servers()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.message
        }
    }

    Group(
        stringResource(R.string.group_player_alerts),
        footer = stringResource(if (alertsOn) R.string.player_alerts_footer else R.string.player_alerts_footer_off),
    ) {
        val list = servers
        val failed = error
        if (list == null) {
            if (failed != null) {
                PushNote(failed) {}
            } else {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                }
            }
        } else if (list.isEmpty()) {
            PushNote(stringResource(R.string.push_setup_no_servers)) {}
        } else {
            list.forEach { s ->
                GroupSwitch(
                    s.name,
                    stringResource(R.string.player_alerts_row),
                    s.id in selected,
                    icon = Icons.Outlined.Group,
                    enabled = alertsOn,
                ) { on ->
                    settings.setPlayerAlerts(s.id, on)
                    selected = settings.playerAlertServers
                }
            }
        }
    }
}

@Composable
private fun PushNote(text: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
        action()
    }
}
