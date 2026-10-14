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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.vknxworks.craftyremote.ui.PillTabs
import com.vknxworks.craftyremote.ui.PillTab
import com.vknxworks.craftyremote.ui.AccentCard
import com.vknxworks.craftyremote.ui.RingGauge
import com.vknxworks.craftyremote.ui.FactChip
import com.vknxworks.craftyremote.ui.ratio
import com.vknxworks.craftyremote.ui.ChartCpu
import com.vknxworks.craftyremote.ui.ChartMem
import com.vknxworks.craftyremote.ui.ChartPlayers
import com.vknxworks.craftyremote.ui.theme.stateColor
import com.vknxworks.craftyremote.data.parseTimestamp
import com.vknxworks.craftyremote.ui.rememberRefreshMs
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import com.vknxworks.craftyremote.data.TaskAction
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

private enum class DetailTab(val title: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Filled.Dashboard),
    CONSOLE("Console", Icons.Filled.Terminal),
    STATS("Stats", Icons.AutoMirrored.Filled.ShowChart),
    FILES("Files", Icons.Filled.FolderOpen),
    BACKUPS("Backups", Icons.Filled.Backup),
    SCHEDULES("Schedules", Icons.Filled.Schedule),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailScreen(
    api: CraftyApi,
    serverId: String,
    serverName: String,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onDeleted: () -> Unit,
    onManage: (ManagePage) -> Unit = {},
    onOpenServer: (id: String, name: String) -> Unit = { _, _ -> },
) {
    val vm: ServerDetailViewModel = viewModel(factory = viewModelFactory { initializer { ServerDetailViewModel(api, serverId) } })
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    var overflow by remember { mutableStateOf(false) }
    var confirmDeleteServer by remember { mutableStateOf(false) }
    var confirmUpdate by remember { mutableStateOf(false) }
    var confirmClone by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val refreshMs = rememberRefreshMs()
    PollWhileVisible(serverId to refreshMs, refreshMs) { vm.refreshStats() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            serverName.ifBlank { "Server" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val sub = listOfNotNull(
                            vm.info?.type?.let(::prettyType),
                            vm.version,
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) {
                            Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    StatusPill(vm.stats?.state)
                    Box {
                        IconButton(onClick = { overflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            ManagePage.entries.forEach { page ->
                                DropdownMenuItem(
                                    text = { Text(page.title) },
                                    leadingIcon = { Icon(page.icon, contentDescription = null) },
                                    onClick = { overflow = false; onManage(page) },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Update server software") },
                                leadingIcon = { Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null) },
                                enabled = vm.info?.isBedrock != true,
                                onClick = { overflow = false; vm.loadConfig(); confirmUpdate = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Clone server") },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                onClick = { overflow = false; confirmClone = true },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Diagnostics") },
                                leadingIcon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                                onClick = { overflow = false; vm.runDiagnostics() },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete server", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { overflow = false; confirmDeleteServer = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PillTabs(
                tabs = remember { DetailTab.entries.map { PillTab(it.title, it.icon) } },
                selected = tab,
                onSelect = { tab = it },
            )
            AnimatedContent(
                targetState = DetailTab.entries[tab],
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "tab",
            ) { current ->
                when (current) {
                    DetailTab.OVERVIEW -> OverviewTab(vm, onManage)
                    DetailTab.CONSOLE -> ConsoleTab(vm)
                    DetailTab.STATS -> StatsTab(vm)
                    DetailTab.FILES -> FilesTab(vm, onOpenFile)
                    DetailTab.BACKUPS -> BackupsTab(vm)
                    DetailTab.SCHEDULES -> SchedulesTab(vm)
                }
            }
        }
    }

    vm.diagnostics?.let { report -> DiagnosticsDialog(report, onClose = vm::closeDiagnostics) }

    if (confirmUpdate) {
        val url = vm.config?.updateUrl
        AlertDialog(
            onDismissRequest = { confirmUpdate = false },
            icon = { Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null) },
            title = { Text("Update server software?") },
            text = {
                Text(
                    if (url == null) {
                        "This server has no update URL set, so Crafty has nothing to download. Add the new jar's download link in Server options first."
                    } else {
                        "Crafty downloads the server jar again from:\n$url\n\nThe server is stopped first and the old jar is replaced. Back up the world before a big version jump."
                    },
                )
            },
            confirmButton = {
                if (url == null) {
                    TextButton(onClick = { confirmUpdate = false; onManage(ManagePage.OPTIONS) }) { Text("Open Server options") }
                } else {
                    TextButton(onClick = { confirmUpdate = false; vm.updateExecutable() }) { Text("Update") }
                }
            },
            dismissButton = { TextButton(onClick = { confirmUpdate = false }) { Text("Cancel") } },
        )
    }

    if (confirmClone) {
        AlertDialog(
            onDismissRequest = { if (!vm.managing) confirmClone = false },
            icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            title = { Text("Clone this server?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Crafty copies the whole server folder (world, configs, jar) and its settings into a new server. Large worlds take a while and use the same disk space again.")
                    if (vm.managing) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !vm.managing,
                    onClick = { vm.cloneServer { id, name -> confirmClone = false; onOpenServer(id, name) } },
                ) { Text("Clone") }
            },
            dismissButton = { TextButton(onClick = { confirmClone = false }, enabled = !vm.managing) { Text("Cancel") } },
        )
    }

    if (confirmDeleteServer) {
        DeleteServerDialog(
            serverName = serverName.ifBlank { vm.info?.name.orEmpty() },
            running = vm.stats?.running == true,
            busy = vm.deletingServer,
            onDismiss = { if (!vm.deletingServer) confirmDeleteServer = false },
            onConfirm = { files -> vm.deleteServer(files) { confirmDeleteServer = false; onDeleted() } },
        )
    }
}

@Composable
private fun DeleteServerDialog(
    serverName: String,
    running: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (deleteFiles: Boolean) -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    var deleteFiles by remember { mutableStateOf(true) }
    val matches = typed.trim() == serverName.trim() && serverName.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete $serverName?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    buildString {
                        append("The server is removed from Crafty")
                        if (running) append(" (it will be stopped first)")
                        append(". This can't be undone.")
                    },
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) { deleteFiles = !deleteFiles },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it }, enabled = !busy)
                    Column {
                        Text("Also delete its files", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (deleteFiles) "The server folder (world, configs, server jar) is permanently deleted."
                            else "Files stay on the Crafty machine; only the server entry is removed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Type the server name to confirm") },
                    placeholder = { Text(serverName) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteFiles) }, enabled = matches && !busy) {
                Text("Delete server", color = if (matches && !busy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun DiagnosticsDialog(report: String, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Diagnostics") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Raw replies from Crafty for backups and schedules. Copy and send them over to get these tabs fixed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    color = ConsoleBackground,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                ) {
                    Text(
                        report,
                        color = ConsoleText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(report))
                    copied = true
                },
            ) { Text(if (copied) "Copied" else "Copy") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

// ---------------------------------------------------------------- overview

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(vm: ServerDetailViewModel, onManage: (ManagePage) -> Unit) {
    val s = vm.stats
    var confirm by remember { mutableStateOf<PowerAction?>(null) }
    // "" opens the sheet with a name field; a name opens it for that player.
    var playerSheet by remember { mutableStateOf<String?>(null) }

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

        if (s != null) StatusHero(s)

        SectionLabel("Power")
        PowerControls(
            state = s?.state,
            pending = vm.pendingAction,
            onAction = { a -> if (a.confirmText != null) confirm = a else vm.runAction(a) },
        )

        if (s != null) {
            SectionLabel("Details")
            // Only facts Crafty actually reported; unknown ones are left out rather than shown as "—".
            val facts = listOfNotNull(
                s.mem?.let { Triple("Memory", it, Icons.Filled.Memory) },
                s.worldSize?.let { Triple("World size", it, Icons.Filled.SdStorage) },
                vm.version?.let { Triple("Version", it, Icons.Filled.Tag) },
                s.port?.let { Triple("Port", it.toString(), Icons.Filled.Public) },
            )
            facts.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { (label, value, icon) -> FactChip(label, value, icon, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            SectionLabel("Players")
            if (s.players.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    s.players.forEach { name ->
                        SuggestionChip(
                            onClick = { playerSheet = name },
                            label = { Text(name) },
                            icon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
                Text(
                    "Tap a player for actions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if ((s.online ?: 0) > 0) {
                Text(
                    "${s.online} online. This server doesn't share player names with Crafty, so type a name to manage someone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { playerSheet = "" },
                enabled = s.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.ManageAccounts, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Manage a player by name…")
            }
        }

        SectionLabel("Manage")
        ManagePage.entries.forEach { page ->
            Card(
                onClick = { onManage(page) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(page.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(page.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (vm.eulaNeeded) {
        val uri = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = vm::dismissEula,
            title = { Text("Accept the Minecraft EULA?") },
            text = {
                Text(
                    "This server won't start until its EULA is accepted. Accepting writes eula=true " +
                        "to the server's eula.txt, the same as accepting it in Crafty's web panel.",
                )
            },
            confirmButton = { TextButton(onClick = vm::acceptEulaAndStart) { Text("Accept & start") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { uri.openUri("https://aka.ms/MinecraftEULA") }) { Text("Read EULA") }
                    TextButton(onClick = vm::dismissEula) { Text("Cancel") }
                }
            },
        )
    }

    playerSheet?.let { name ->
        PlayerSheet(
            fixedName = name.ifEmpty { null },
            isBedrock = vm.info?.isBedrock == true,
            onDismiss = { playerSheet = null },
            onCommand = vm::playerCommand,
        )
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
private fun StatusHero(s: ServerStats) {
    AccentCard(accent = stateColor(s.state), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.state.label, style = MaterialTheme.typography.headlineMedium)
                val detail = when (s.state) {
                    ServerState.RUNNING -> s.started?.let { uptimeText(it) }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RingGauge(
                    fraction = s.cpu?.let { (it / 100).toFloat() },
                    value = s.cpu?.let { "%.0f%%".format(it) } ?: "—",
                    label = "CPU",
                    color = ChartCpu,
                )
                RingGauge(
                    fraction = s.memPercent?.let { (it / 100).toFloat() },
                    value = s.memPercent?.let { "%.0f%%".format(it) } ?: "—",
                    label = "Memory",
                    color = ChartMem,
                )
                if (s.playersKnown) {
                    RingGauge(
                        fraction = ratio(s.online, s.max),
                        value = s.online?.toString() ?: "—",
                        label = s.max?.takeIf { it > 0 }?.let { "of $it players" } ?: "Players",
                        color = ChartPlayers,
                    )
                }
            }
        }
    }
}

/** "Up 1 d 3 h" from Crafty's start timestamp; falls back to the raw text. */
private fun uptimeText(started: String): String {
    val t = parseTimestamp(started) ?: return "Up since $started"
    val mins = ((System.currentTimeMillis() - t) / 60_000).coerceAtLeast(0)
    val d = mins / 1440
    val h = (mins % 1440) / 60
    val m = mins % 60
    val span = when {
        d > 0 -> "$d d $h h"
        h > 0 -> "$h h $m min"
        else -> "$m min"
    }
    return "Up $span"
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

@Composable
private fun lineColor(line: String): Color = when {
    "ERROR" in line || "SEVERE" in line || "Exception" in line -> ConsoleError
    "WARN" in line -> ConsoleWarn
    line.startsWith("[Crafty]") || line.startsWith("Crafty") -> ConsoleDim
    else -> ConsoleText
}

@Composable
private fun ConsoleTab(vm: ServerDetailViewModel) {
    val consoleMs = rememberRefreshMs().coerceAtMost(2_000)
    PollWhileVisible("console-${vm.serverId}" to consoleMs, consoleMs) { vm.refreshLogs() }

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
    var editingTask by remember { mutableStateOf<TaskEdit?>(null) }
    var editingBackup by remember { mutableStateOf<BackupConfig?>(null) }
    var restoring by remember { mutableStateOf<BackupConfig?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            TabHeader("Backup profiles", state.loading) {
                vm.loadBackups()
                vm.loadTasks()
            }
        }
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
            val backupTasks = vm.tasks.data.orEmpty().filter { it.kind == TaskAction.BACKUP }
            items(list) { b ->
                // A backup task names its config by id; tasks without one run the default config.
                val mine = backupTasks.filter { t -> t.actionId == b.id || (t.actionId == null && b.isDefault) }
                BackupCard(
                    b,
                    schedules = mine,
                    schedulesLoading = vm.tasks.loading,
                    schedulesError = vm.tasks.error,
                    busy = vm.backupInFlight == (b.id ?: ServerDetailViewModel.DEFAULT_BACKUP),
                    enabled = vm.backupInFlight == null,
                    onToggle = { t, on -> vm.setTaskEnabled(t, on) },
                    onEditTask = { t -> editingTask = TaskEdit(t, null) },
                    onAddSchedule = { editingTask = TaskEdit(null, TaskAction.BACKUP to b.id) },
                    onEdit = { editingBackup = b },
                    onRestore = { vm.loadBackupFiles(b); restoring = b },
                ) { vm.runBackup(b) }
            }
        }
        item(key = "note") {
            Text(
                "Backups run on the Crafty host. Downloading backup archives stays in the web panel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editingTask?.let { e -> TaskEditor(vm, e) { editingTask = null } }
    restoring?.let { b ->
        RestoreDialog(
            config = b,
            files = vm.backupFiles,
            busy = vm.managing,
            running = vm.stats?.running == true,
            onDismiss = { if (!vm.managing) restoring = null },
            onRestore = { name, inPlace -> vm.restoreBackup(b, name, inPlace) { restoring = null } },
        )
    }
    editingBackup?.let { b ->
        BackupEditorSheet(
            config = b,
            saving = vm.savingEdit,
            onDismiss = { editingBackup = null },
            onSave = { fields -> vm.saveBackup(b, fields) { editingBackup = null } },
        )
    }
}

@Composable
private fun BackupCard(
    b: BackupConfig,
    schedules: List<ScheduledTask>,
    schedulesLoading: Boolean,
    schedulesError: String?,
    busy: Boolean,
    enabled: Boolean,
    onToggle: (ScheduledTask, Boolean) -> Unit,
    onEditTask: (ScheduledTask) -> Unit,
    onAddSchedule: () -> Unit,
    onEdit: () -> Unit,
    onRestore: () -> Unit,
    onRun: () -> Unit,
) {
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
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Text("Schedule", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            when {
                schedules.isNotEmpty() -> schedules.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onEditTask(t) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp),
                            tint = if (t.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.schedule, style = MaterialTheme.typography.bodyMedium)
                            if (t.name != t.actionLabel) {
                                Text(t.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(checked = t.enabled, onCheckedChange = { onToggle(t, it) })
                    }
                }
                schedulesLoading -> Text("Loading schedule…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                schedulesError != null -> Text("Couldn't load schedules: $schedulesError", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else -> Text(
                    "Not scheduled yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAddSchedule, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add schedule")
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Settings")
                }
            }
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Restore a backup…")
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
    LaunchedEffect(vm) {
        if (vm.tasks.data == null) vm.loadTasks()
        // The editor offers backup profiles, so have them ready.
        if (vm.backups.data == null) vm.loadBackups()
    }
    val state = vm.tasks
    var editing by remember { mutableStateOf<TaskEdit?>(null) }
    var confirmRun by remember { mutableStateOf<ScheduledTask?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { TabHeader("Scheduled tasks", state.loading, vm::loadTasks) }
        item(key = "new") {
            FilledTonalButton(onClick = { editing = TaskEdit(null, null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New schedule")
            }
        }
        state.error?.let { item(key = "error") { ErrorBanner(it, onRetry = vm::loadTasks) } }

        val list = state.data
        if (list != null && list.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    title = "No scheduled tasks",
                    body = "Tap New schedule to restart, back up, or run a command automatically.",
                )
            }
        }
        if (list != null) {
            items(list, key = { it.id }) { t ->
                TaskCard(t, onClick = { editing = TaskEdit(t, null) }, onRun = { confirmRun = t }) { enabled -> vm.setTaskEnabled(t, enabled) }
            }
        }
    }

    editing?.let { e -> TaskEditor(vm, e) { editing = null } }
    confirmRun?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmRun = null },
            title = { Text("Run ${t.name} now?") },
            text = { Text(t.command?.let { "${t.actionLabel}: $it" } ?: t.actionLabel) },
            confirmButton = { TextButton(onClick = { confirmRun = null; vm.runTaskNow(t) }) { Text("Run now") } },
            dismissButton = { TextButton(onClick = { confirmRun = null }) { Text("Cancel") } },
        )
    }
}

/** What the task editor is opened for: an existing task, or a new one with an optional preset action. */
private data class TaskEdit(val task: ScheduledTask?, val preset: Pair<TaskAction, String?>?)

@Composable
private fun TaskEditor(vm: ServerDetailViewModel, e: TaskEdit, close: () -> Unit) {
    TaskEditorSheet(
        existing = e.task,
        backups = vm.backups.data.orEmpty(),
        saving = vm.savingEdit,
        preset = e.preset,
        onDismiss = close,
        onSave = { fields -> vm.saveTask(e.task, fields, close) },
        onDelete = e.task?.let { t -> { vm.deleteTask(t, close) } },
    )
}

@Composable
private fun TaskCard(t: ScheduledTask, onClick: () -> Unit, onRun: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(
        onClick = onClick,
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
            IconButton(onClick = onRun) { Icon(Icons.Filled.PlayArrow, contentDescription = "Run now") }
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

// ---------------------------------------------------------------- management pages

/** The full-screen management pages reachable from a server. */
enum class ManagePage(val route: String, val title: String, val subtitle: String, val icon: ImageVector) {
    PROPERTIES("properties", "Server settings", "Everything in server.properties", Icons.Filled.Tune),
    PLAYERS("players", "Players & bans", "Whitelist, operators and bans", Icons.Filled.Group),
    OPTIONS("options", "Server options", "Name, memory, auto-start, crash detection", Icons.Filled.Settings),
}

@Composable
private fun RestoreDialog(
    config: BackupConfig,
    files: com.vknxworks.craftyremote.ui.Loadable<List<String>>,
    busy: Boolean,
    running: Boolean,
    onDismiss: () -> Unit,
    onRestore: (fileName: String, inPlace: Boolean) -> Unit,
) {
    var picked by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }
    var inPlace by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf("") }
    val list = files.data.orEmpty()
    val name = (picked ?: typed).trim()
    val armed = confirm.trim().equals("restore", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.SettingsBackupRestore, contentDescription = null) },
        title = { Text("Restore from ${config.name}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    files.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    list.isNotEmpty() -> {
                        Text("Pick a backup", style = MaterialTheme.typography.labelLarge)
                        list.take(30).forEach { f ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !busy) { picked = f }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.RadioButton(selected = picked == f, onClick = { picked = f }, enabled = !busy)
                                Text(f, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    else -> {
                        Text(
                            "Crafty doesn't list backup files through its API, so type the archive's file name " +
                                "(see the Backups page in the web panel, e.g. 2026-10-14_03-00-00.zip).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it },
                            label = { Text("Backup file name") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) { inPlace = !inPlace },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = inPlace, onCheckedChange = { inPlace = it }, enabled = !busy)
                    Column {
                        Text("Restore in place", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (inPlace) "Backup files overwrite the current ones; files not in the backup stay."
                            else "The server folder is cleared first, so it ends up exactly like the backup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    (if (running) "The server is stopped first. " else "") +
                        "Everything since this backup is lost. Type RESTORE to confirm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    placeholder = { Text("RESTORE") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onRestore(name, inPlace) }, enabled = name.isNotEmpty() && armed && !busy) {
                Text("Restore", color = if (name.isNotEmpty() && armed && !busy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
