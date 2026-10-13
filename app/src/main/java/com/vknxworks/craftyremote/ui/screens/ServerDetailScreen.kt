@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vknxworks.craftyremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vknxworks.craftyremote.data.BackupConfig
import com.vknxworks.craftyremote.data.CraftyApi
import com.vknxworks.craftyremote.data.ScheduledTask
import com.vknxworks.craftyremote.data.ServerState
import com.vknxworks.craftyremote.data.ServerStats
import com.vknxworks.craftyremote.ui.ErrorBanner
import com.vknxworks.craftyremote.ui.MetricTile
import com.vknxworks.craftyremote.ui.PollWhileVisible
import com.vknxworks.craftyremote.ui.PowerAction
import com.vknxworks.craftyremote.ui.SectionLabel
import com.vknxworks.craftyremote.ui.ServerDetailViewModel
import com.vknxworks.craftyremote.ui.StatusPill
import com.vknxworks.craftyremote.ui.theme.ConsoleBackground
import com.vknxworks.craftyremote.ui.theme.ConsoleDim
import com.vknxworks.craftyremote.ui.theme.ConsoleError
import com.vknxworks.craftyremote.ui.theme.ConsoleText
import com.vknxworks.craftyremote.ui.theme.ConsoleWarn

private enum class DetailTab(val title: String) { OVERVIEW("Overview"), CONSOLE("Console"), BACKUPS("Backups"), SCHEDULES("Schedules") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailScreen(
    api: CraftyApi,
    serverId: String,
    serverName: String,
    onBack: () -> Unit,
) {
    val vm: ServerDetailViewModel = viewModel(factory = viewModelFactory { initializer { ServerDetailViewModel(api, serverId) } })
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    PollWhileVisible(serverId, 3_000) { vm.refreshStats() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        serverName.ifBlank { "Server" },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    StatusPill(vm.stats?.state)
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = tab) {
                DetailTab.entries.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.title, maxLines = 1) })
                }
            }
            when (DetailTab.entries[tab]) {
                DetailTab.OVERVIEW -> OverviewTab(vm)
                DetailTab.CONSOLE -> ConsoleTab(vm)
                DetailTab.BACKUPS -> BackupsTab(vm)
                DetailTab.SCHEDULES -> SchedulesTab(vm)
            }
        }
    }
}

// ---------------------------------------------------------------- overview

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(vm: ServerDetailViewModel) {
    val s = vm.stats
    var confirm by remember { mutableStateOf<PowerAction?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        vm.statsError?.let { ErrorBanner(it) }
        if (s == null && vm.statsError == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (s != null) StatusSummary(s)

        SectionLabel("Power")
        PowerControls(
            state = s?.state,
            pending = vm.pendingAction,
            onAction = { a -> if (a.confirmText != null) confirm = a else vm.runAction(a) },
        )

        if (s != null) {
            SectionLabel("Live stats")
            TileRow(
                { MetricTile("CPU", s.cpu?.let { "%.1f%%".format(it) } ?: "—", Icons.Filled.Speed, it) },
                { MetricTile("Memory", memoryText(s), Icons.Filled.Memory, it) },
            )
            TileRow(
                { MetricTile("Players", playersText(s.online, s.max), Icons.Filled.Group, it) },
                { MetricTile("Version", s.version ?: "—", Icons.Filled.Tag, it) },
            )
            TileRow(
                { MetricTile("World size", s.worldSize ?: "—", Icons.Filled.SdStorage, it) },
                { MetricTile("Port", s.port?.toString() ?: "—", Icons.Filled.Public, it) },
            )

            if (s.players.isNotEmpty()) {
                SectionLabel("Online now")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    s.players.forEach { name ->
                        SuggestionChip(onClick = {}, label = { Text(name) })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("${action.label} server?") },
            text = { Text(action.confirmText.orEmpty()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = null
                        vm.runAction(action)
                    },
                ) {
                    Text(
                        action.label,
                        color = if (action.dangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusSummary(s: ServerStats) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.state.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val detail = when (s.state) {
                ServerState.RUNNING -> s.started?.let { "Up since $it" }
                ServerState.CRASHED -> "Crafty detected a crash."
                ServerState.STARTING -> "Waiting for the server to come up…"
                ServerState.UPDATING -> "Updating the server executable…"
                ServerState.STOPPED -> "Not running."
            }
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TileRow(
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        left(Modifier.weight(1f))
        right(Modifier.weight(1f))
    }
}

private fun memoryText(s: ServerStats): String {
    val pct = s.memPercent?.let { "%.0f%%".format(it) }
    return listOfNotNull(s.mem, pct).joinToString(" · ").ifEmpty { "—" }
}

@Composable
private fun PowerControls(state: ServerState?, pending: PowerAction?, onAction: (PowerAction) -> Unit) {
    val live = state == ServerState.RUNNING || state == ServerState.STARTING
    val stopped = state == ServerState.STOPPED || state == ServerState.CRASHED
    val idle = pending == null

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PowerButton(PowerAction.START, Icons.Filled.PlayArrow, stopped && idle, pending, Modifier.weight(1f), onAction)
            PowerButton(PowerAction.RESTART, Icons.Filled.RestartAlt, live && idle, pending, Modifier.weight(1f), onAction)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PowerButton(PowerAction.STOP, Icons.Filled.Stop, live && idle, pending, Modifier.weight(1f), onAction)
            PowerButton(PowerAction.KILL, Icons.Filled.Dangerous, (live || state == ServerState.UPDATING) && idle, pending, Modifier.weight(1f), onAction)
        }
    }
}

@Composable
private fun PowerButton(
    action: PowerAction,
    icon: ImageVector,
    enabled: Boolean,
    pending: PowerAction?,
    modifier: Modifier,
    onAction: (PowerAction) -> Unit,
) {
    val content: @Composable () -> Unit = {
        if (pending == action) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(action.label)
    }
    if (action.dangerous) {
        OutlinedButton(
            onClick = { onAction(action) },
            enabled = enabled,
            modifier = modifier.height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { content() }
    } else {
        FilledTonalButton(
            onClick = { onAction(action) },
            enabled = enabled,
            modifier = modifier.height(48.dp),
        ) { content() }
    }
}

// ---------------------------------------------------------------- console

private val presetCommands = listOf("list", "save-all", "say ", "weather clear", "time set day", "whitelist list")

private fun lineColor(line: String): Color = when {
    "ERROR" in line || "SEVERE" in line || "Exception" in line -> ConsoleError
    "WARN" in line -> ConsoleWarn
    line.startsWith("[Crafty]") || line.startsWith("Crafty") -> ConsoleDim
    else -> ConsoleText
}

@Composable
private fun ConsoleTab(vm: ServerDetailViewModel) {
    PollWhileVisible("console-${vm.serverId}", 2_000) { vm.refreshLogs() }

    val listState = rememberLazyListState()
    var follow by rememberSaveable { mutableStateOf(true) }
    var input by rememberSaveable { mutableStateOf("") }

    // Scrolling up to read pauses auto-follow; tap Follow to resume.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && listState.canScrollForward) follow = false
    }
    LaunchedEffect(vm.logs, follow) {
        if (follow && vm.logs.isNotEmpty()) listState.scrollToItem(vm.logs.lastIndex)
    }

    val send: () -> Unit = {
        if (input.isNotBlank()) {
            vm.sendCommand(input) { input = "" }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = follow,
                onClick = { follow = !follow },
                label = { Text("Follow") },
                leadingIcon = if (follow) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${vm.logs.size} lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        vm.consoleError?.let {
            ErrorBanner(it, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
        }

        Surface(
            color = ConsoleBackground,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            LazyColumn(state = listState, contentPadding = PaddingValues(12.dp)) {
                itemsIndexed(vm.logs) { _, line ->
                    Text(
                        line,
                        color = lineColor(line),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val chips = (vm.recentCommands + presetCommands).distinct()
            items(chips) { cmd ->
                SuggestionChip(
                    onClick = { input = cmd },
                    label = { Text(cmd.trim(), fontFamily = FontFamily.Monospace) },
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Command, without /") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { send() },
                enabled = input.isNotBlank() && !vm.sending,
                modifier = Modifier.size(52.dp),
            ) {
                if (vm.sending) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- backups

@Composable
private fun BackupsTab(vm: ServerDetailViewModel) {
    LaunchedEffect(vm) { if (vm.backups.data == null) vm.loadBackups() }
    val state = vm.backups

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { TabHeader("Backup profiles", state.loading, vm::loadBackups) }
        state.error?.let { item(key = "error") { ErrorBanner(it, onRetry = vm::loadBackups) } }

        val list = state.data
        if (list != null && list.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    title = "No backup profiles found",
                    body = "You can still trigger the server's default backup.",
                    action = "Back up now",
                    busy = vm.backupInFlight != null,
                    onAction = { vm.runBackup(null) },
                )
            }
        }
        if (list != null) {
            items(list) { b ->
                BackupCard(b, busy = vm.backupInFlight == (b.id ?: ServerDetailViewModel.DEFAULT_BACKUP), enabled = vm.backupInFlight == null) { vm.runBackup(b) }
            }
        }
        item(key = "note") {
            Text(
                "Backups run on the Crafty host. Restoring and downloading backups stays in the web panel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackupCard(b: BackupConfig, busy: Boolean, enabled: Boolean, onRun: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    b.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (b.isDefault) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "Default",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            b.details.forEach { (label, value) ->
                Row {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(120.dp),
                    )
                    Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            FilledTonalButton(onClick = onRun, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Back up now")
            }
        }
    }
}

// ---------------------------------------------------------------- schedules

@Composable
private fun SchedulesTab(vm: ServerDetailViewModel) {
    LaunchedEffect(vm) { if (vm.tasks.data == null) vm.loadTasks() }
    val state = vm.tasks

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { TabHeader("Scheduled tasks", state.loading, vm::loadTasks) }
        state.error?.let { item(key = "error") { ErrorBanner(it, onRetry = vm::loadTasks) } }

        val list = state.data
        if (list != null && list.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    title = "No scheduled tasks",
                    body = "Create schedules in the Crafty web panel; they'll show up here to switch on and off.",
                )
            }
        }
        if (list != null) {
            items(list, key = { it.id }) { t -> TaskCard(t) { enabled -> vm.setTaskEnabled(t, enabled) } }
        }
    }
}

@Composable
private fun TaskCard(t: ScheduledTask, onToggle: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Schedule, contentDescription = null,
                tint = if (t.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(t.schedule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val what = t.command?.let { "${t.actionLabel}: $it" } ?: t.actionLabel
                Text(
                    what,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (t.command != null) FontFamily.Monospace else FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = t.enabled, onCheckedChange = onToggle)
        }
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
private fun TabHeader(title: String, loading: Boolean, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title, Modifier.weight(1f))
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp).padding(2.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(14.dp))
        } else {
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
        }
    }
}

@Composable
private fun EmptyCard(
    title: String,
    body: String,
    action: String? = null,
    busy: Boolean = false,
    onAction: () -> Unit = {},
) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                FilledTonalButton(onClick = onAction, enabled = !busy) { Text(action) }
            }
        }
    }
}
