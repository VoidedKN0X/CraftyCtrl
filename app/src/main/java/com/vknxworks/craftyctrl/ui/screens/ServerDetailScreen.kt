@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vknxworks.craftyctrl.ui.screens

import com.vknxworks.craftyctrl.ui.lock.findFragmentActivity
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
import com.vknxworks.craftyctrl.ui.PillTabs
import com.vknxworks.craftyctrl.ui.PillTab
import com.vknxworks.craftyctrl.ui.AccentCard
import com.vknxworks.craftyctrl.ui.RingGauge
import com.vknxworks.craftyctrl.ui.FactChip
import com.vknxworks.craftyctrl.ui.ratio
import com.vknxworks.craftyctrl.ui.ChartCpu
import com.vknxworks.craftyctrl.ui.ChartMem
import com.vknxworks.craftyctrl.ui.ChartPlayers
import com.vknxworks.craftyctrl.ui.theme.stateColor
import com.vknxworks.craftyctrl.ui.Countdown
import com.vknxworks.craftyctrl.ui.Countdowns
import com.vknxworks.craftyctrl.ui.verifyUser
import com.vknxworks.craftyctrl.ui.IslandTab
import com.vknxworks.craftyctrl.ui.TabIsland
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import com.vknxworks.craftyctrl.ui.CtrlCard
import com.vknxworks.craftyctrl.ui.StatRow
import com.vknxworks.craftyctrl.ui.BigStat
import com.vknxworks.craftyctrl.ui.ItemDivider
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Description
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.platform.LocalContext
import com.vknxworks.craftyctrl.ui.theme.Mono
import com.vknxworks.craftyctrl.ui.BigMetric
import com.vknxworks.craftyctrl.ui.DetailRow
import com.vknxworks.craftyctrl.ui.Dot
import androidx.compose.material3.Button
import com.vknxworks.craftyctrl.data.parseTimestamp
import com.vknxworks.craftyctrl.ui.rememberRefreshMs
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vknxworks.craftyctrl.data.BackupConfig
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.ScheduledTask
import com.vknxworks.craftyctrl.data.TaskAction
import com.vknxworks.craftyctrl.data.ServerState
import com.vknxworks.craftyctrl.data.ServerStats
import com.vknxworks.craftyctrl.ui.ErrorBanner
import com.vknxworks.craftyctrl.ui.MetricTile
import com.vknxworks.craftyctrl.ui.PollWhileVisible
import com.vknxworks.craftyctrl.ui.PowerAction
import com.vknxworks.craftyctrl.ui.SectionLabel
import com.vknxworks.craftyctrl.ui.ServerDetailViewModel
import com.vknxworks.craftyctrl.ui.StatusPill
import com.vknxworks.craftyctrl.ui.theme.ConsoleBackground
import com.vknxworks.craftyctrl.ui.theme.ConsoleDim
import com.vknxworks.craftyctrl.ui.theme.ConsoleError
import com.vknxworks.craftyctrl.ui.theme.ConsoleText
import com.vknxworks.craftyctrl.ui.theme.ConsoleWarn
import androidx.annotation.StringRes
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str
import androidx.compose.ui.res.stringResource

private enum class DetailTab(@StringRes private val titleRes: Int, val icon: ImageVector) {
    OVERVIEW(R.string.tab_overview, Icons.Filled.Dashboard),
    CONSOLE(R.string.tab_console, Icons.Filled.Terminal),
    STATS(R.string.tab_stats, Icons.AutoMirrored.Filled.ShowChart),
    FILES(R.string.tab_files, Icons.Filled.FolderOpen),
    BACKUPS(R.string.tab_backups, Icons.Filled.Backup),
    SCHEDULES(R.string.tab_schedules, Icons.Filled.Schedule);

    val title: String get() = str(titleRes)
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
    val activity = LocalContext.current.findFragmentActivity()
    var confirmUpdate by remember { mutableStateOf(false) }
    var confirmClone by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val refreshMs = rememberRefreshMs()
    PollWhileVisible(serverId to refreshMs, refreshMs) { vm.refreshStats() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 78.dp)) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            serverName.ifBlank { stringResource(R.string.server) },
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { overflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
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
                                text = { Text(stringResource(R.string.update_server_software)) },
                                leadingIcon = { Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null) },
                                enabled = vm.info?.isBedrock != true,
                                onClick = { overflow = false; vm.loadConfig(); confirmUpdate = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clone_server)) },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                onClick = { overflow = false; confirmClone = true },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.diagnostics)) },
                                leadingIcon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                                onClick = { overflow = false; vm.runDiagnostics() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_server), color = MaterialTheme.colorScheme.error) },
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
        // The tabs float at the bottom as a frosted island, the same as on the home screen.
        // It steps aside while the keyboard is open so the console's command field stays visible.
        val haze = remember { HazeState() }
        val imeVisible = WindowInsets.isImeVisible
        val islandSpace = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 78.dp
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(haze)
                    .then(if (imeVisible) Modifier.imePadding() else Modifier.padding(bottom = islandSpace)),
            ) {
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
            if (!imeVisible) {
                TabIsland(
                    tabs = DetailTab.entries.mapIndexed { i, t -> IslandTab(i, t.icon, t.title) },
                    selected = tab,
                    hazeState = haze,
                    onSelect = { tab = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    vm.diagnostics?.let { report -> DiagnosticsDialog(report, onClose = vm::closeDiagnostics) }

    if (confirmUpdate) {
        val url = vm.config?.updateUrl
        AlertDialog(
            onDismissRequest = { confirmUpdate = false },
            icon = { Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null) },
            title = { Text(stringResource(R.string.update_server_software_2)) },
            text = {
                Text(
                    if (url == null) {
                        stringResource(R.string.this_server_has_no_update_url)
                    } else {
                        stringResource(R.string.crafty_downloads_the_server_jar_again, url)
                    },
                )
            },
            confirmButton = {
                if (url == null) {
                    TextButton(onClick = { confirmUpdate = false; onManage(ManagePage.OPTIONS) }) { Text(stringResource(R.string.open_server_options)) }
                } else {
                    TextButton(onClick = {
                        confirmUpdate = false
                        verifyUser(activity, vm.info?.name?.let { str(R.string.update_x, it) } ?: str(R.string.update_server), str(R.string.the_server_is_stopped_first), onSuccess = vm::updateExecutable)
                    }) { Text(stringResource(R.string.update)) }
                }
            },
            dismissButton = { TextButton(onClick = { confirmUpdate = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmClone) {
        AlertDialog(
            onDismissRequest = { if (!vm.managing) confirmClone = false },
            icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            title = { Text(stringResource(R.string.clone_this_server)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.crafty_copies_the_whole_server_folder))
                    if (vm.managing) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !vm.managing,
                    onClick = { vm.cloneServer { id, name -> confirmClone = false; onOpenServer(id, name) } },
                ) { Text(stringResource(R.string.clone)) }
            },
            dismissButton = { TextButton(onClick = { confirmClone = false }, enabled = !vm.managing) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmDeleteServer) {
        DeleteServerDialog(
            serverName = serverName.ifBlank { vm.info?.name.orEmpty() },
            running = vm.stats?.running == true,
            busy = vm.deletingServer,
            onDismiss = { if (!vm.deletingServer) confirmDeleteServer = false },
            onConfirm = { files ->
                verifyUser(activity, vm.info?.name?.let { str(R.string.delete_x, it) } ?: str(R.string.delete_server), str(R.string.confirm_it_s_you), onSuccess = {
                    vm.deleteServer(files) { confirmDeleteServer = false; onDeleted() }
                })
            },
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
        title = { Text(stringResource(R.string.delete_x_2, serverName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (running) stringResource(R.string.delete_server_body_running) else stringResource(R.string.delete_server_body),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) { deleteFiles = !deleteFiles },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it }, enabled = !busy)
                    Column {
                        Text(stringResource(R.string.also_delete_its_files), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (deleteFiles) stringResource(R.string.the_server_folder_world_configs_server)
                            else stringResource(R.string.files_stay_on_the_crafty_machine),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.type_the_server_name_to_confirm)) },
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
                Text(stringResource(R.string.delete_server), color = if (matches && !busy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DiagnosticsDialog(report: String, onClose: () -> Unit) {
    val clipboard = com.vknxworks.craftyctrl.ui.rememberSensitiveClipboard()
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.diagnostics)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.raw_replies_from_crafty_for_backups),
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
                        fontFamily = Mono,
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
                    clipboard.copy(report, str(R.string.craftyctrl_diagnostics))
                    copied = true
                },
            ) { Text(if (copied) stringResource(R.string.copied) else stringResource(R.string.copy)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
    )
}

// ---------------------------------------------------------------- overview

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(vm: ServerDetailViewModel, onManage: (ManagePage) -> Unit) {
    val s = vm.stats
    val activity = LocalContext.current.findFragmentActivity()
    var confirm by remember { mutableStateOf<PowerAction?>(null) }
    // "" opens the sheet with a name field; a name opens it for that player.
    var playerSheet by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        vm.statsError?.let { ErrorBanner(it) }
        if (s == null && vm.statsError == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        CtrlCard {
            if (s != null) StatusHero(s)
            Countdowns.active[vm.serverId]?.let { c -> CountdownBanner(c, onCancel = vm::cancelCountdown) }
            PowerControls(
                state = s?.state,
                pending = vm.pendingAction,
                onAction = { a -> if (a.confirmText != null) confirm = a else vm.runAction(a) },
            )
        }

        if (s != null && s.running) {
            CtrlCard(stringResource(R.string.players)) {
                if (s.playersKnown) StatRow(stringResource(R.string.online), playersText(s.online, s.max))
                if (s.players.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        s.players.forEach { name ->
                            AssistChip(
                                onClick = { playerSheet = name },
                                label = { Text(name) },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                } else if ((s.online ?: 0) > 0) {
                    Text(
                        stringResource(R.string.this_server_doesn_t_share_player),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { playerSheet = "" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ManageAccounts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.manage_a_player_by_name))
                }
            }
        }

        if (s != null) {
            // Only facts Crafty actually reported; unknown ones are left out rather than shown as "—".
            val facts = listOfNotNull(
                vm.version?.let { stringResource(R.string.fact_version) to it },
                s.port?.let { stringResource(R.string.fact_port) to it.toString() },
                s.worldName?.let { stringResource(R.string.fact_world) to it },
                s.worldSize?.let { stringResource(R.string.world_size) to it },
                s.mem?.let { stringResource(R.string.memory) to it },
            )
            if (facts.isNotEmpty()) {
                CtrlCard(stringResource(R.string.details)) { facts.forEach { (k, v) -> StatRow(k, v) } }
            }
        }

        Text(
            stringResource(R.string.manage),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 6.dp),
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ManagePage.entries.forEachIndexed { idx, page ->
                if (idx > 0) ItemDivider()
                ListItem(
                    leadingContent = { Icon(page.icon, contentDescription = null) },
                    headlineContent = { Text(page.title) },
                    supportingContent = { Text(page.subtitle) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onManage(page) },
                )
            }
        }
    }

    if (vm.eulaNeeded) {
        val uri = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = vm::dismissEula,
            title = { Text(stringResource(R.string.accept_the_minecraft_eula)) },
            text = {
                Text(
                    stringResource(R.string.this_server_won_t_start_until),
                )
            },
            confirmButton = { TextButton(onClick = vm::acceptEulaAndStart) { Text(stringResource(R.string.accept_start)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { com.vknxworks.craftyctrl.data.lock.Lock.expectReturn(); uri.openUri("https://aka.ms/MinecraftEULA") }) { Text(stringResource(R.string.read_eula)) }
                    TextButton(onClick = vm::dismissEula) { Text(stringResource(R.string.cancel)) }
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
        PowerDialog(
            action = action,
            serverName = vm.info?.name.orEmpty(),
            onDismiss = { confirm = null },
            onConfirm = { delaySeconds, note ->
                confirm = null
                val title = "${action.label} ${vm.info?.name ?: "server"}"
                val subtitle = if (delaySeconds > 0) str(R.string.after_a_x_warning, Countdowns.human(delaySeconds)) else str(R.string.confirm_it_s_you)
                verifyUser(
                    activity, title, subtitle,
                    onSuccess = {
                        if (delaySeconds > 0) vm.startCountdown(action, delaySeconds, note) else vm.runAction(action)
                    },
                )
            },
        )
    }
}

@Composable
private fun StatusHero(s: ServerStats) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(stateColor(s.state), size = 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.state.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val detail = when (s.state) {
                    ServerState.RUNNING -> s.started?.let { uptimeShort(it) }?.let { stringResource(R.string.up_x, it) }
                    ServerState.CRASHED -> stringResource(R.string.crafty_detected_a_crash)
                    ServerState.STARTING -> stringResource(R.string.coming_up)
                    ServerState.UPDATING -> stringResource(R.string.updating_the_server_jar)
                    ServerState.STOPPED -> stringResource(R.string.not_running)
                }
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        s.description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (s.running || s.state == ServerState.STARTING) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("CPU", s.cpu?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
                BigStat(stringResource(R.string.memory), s.memPercent?.let { "%.0f%%".format(it) } ?: s.mem?.replace(" ", "") ?: "—", Modifier.weight(1f))
                if (s.playersKnown) BigStat(stringResource(R.string.players), playersText(s.online, s.max).replace(" ", ""), Modifier.weight(1f))
            }
        }
    }
}

/** "Up 1 d 3 h" from Crafty's start timestamp; falls back to the raw text. */
private fun uptimeText(started: String): String {
    val t = parseTimestamp(started) ?: return str(R.string.up_since_x, started)
    val mins = ((System.currentTimeMillis() - t) / 60_000).coerceAtLeast(0)
    val d = mins / 1440
    val h = (mins % 1440) / 60
    val m = mins % 60
    val span = when {
        d > 0 -> str(R.string.x_d_x_h, d, h)
        h > 0 -> str(R.string.x_h_x_min, h, m)
        else -> str(R.string.x_min, m)
    }
    return str(R.string.up_x, span)
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
    val idle = pending == null
    when {
        live -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PowerButton(PowerAction.RESTART, Icons.Filled.RestartAlt, idle, pending, Modifier.weight(1f), onAction)
            PowerButton(PowerAction.STOP, Icons.Filled.Stop, idle, pending, Modifier.weight(1f), onAction)
            PowerButton(PowerAction.KILL, Icons.Filled.Dangerous, idle, pending, Modifier.weight(1f), onAction)
        }
        state == ServerState.UPDATING -> PowerButton(PowerAction.KILL, Icons.Filled.Dangerous, idle, pending, Modifier.fillMaxWidth(), onAction)
        else -> Button(
            onClick = { onAction(PowerAction.START) },
            enabled = idle && state != null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (pending == PowerAction.START) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.task_start))
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
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(action.label, maxLines = 1)
    }
    if (action.dangerous) {
        OutlinedButton(
            onClick = { onAction(action) },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = modifier.height(48.dp),
        ) { content() }
    } else {
        FilledTonalButton(
            onClick = { onAction(action) },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = modifier.height(48.dp),
        ) { content() }
    }
}

// ---------------------------------------------------------------- console

private val presetCommands = listOf("list", "save-all", "say ", "weather clear", "time set day", "whitelist list")

@Composable
internal fun lineColor(line: String): Color = when {
    isError(line) -> ConsoleError
    isWarn(line) -> ConsoleWarn
    line.startsWith("[Crafty]") || line.startsWith("Crafty") -> ConsoleDim
    else -> ConsoleText
}

private enum class LogLevel(@StringRes private val labelRes: Int) {
    ALL(R.string.level_all), WARN(R.string.level_warnings), ERROR(R.string.level_errors);

    val label: String get() = str(labelRes)
}

internal fun isError(line: String) = "ERROR" in line || "SEVERE" in line || "Exception" in line
internal fun isWarn(line: String) = "WARN" in line

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConsoleTab(vm: ServerDetailViewModel) {
    val consoleMs = rememberRefreshMs().coerceAtMost(2_000)
    PollWhileVisible("console-${vm.serverId}" to consoleMs, consoleMs) { vm.refreshLogs() }

    val listState = rememberLazyListState()
    var follow by rememberSaveable { mutableStateOf(true) }
    var input by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(LogLevel.ALL) }
    val clipboard = com.vknxworks.craftyctrl.ui.rememberSensitiveClipboard()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    val shown = remember(vm.logs, query, level, searching) {
        vm.logs.filter { line ->
            (level == LogLevel.ALL || (level == LogLevel.ERROR && isError(line)) || (level == LogLevel.WARN && (isWarn(line) || isError(line)))) &&
                (!searching || query.isBlank() || line.contains(query, ignoreCase = true))
        }
    }

    // Scrolling up to read pauses auto-follow; tap Follow to resume.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && listState.canScrollForward) follow = false
    }
    LaunchedEffect(shown, follow) {
        if (follow && shown.isNotEmpty()) listState.scrollToItem(shown.lastIndex)
    }

    val send: () -> Unit = {
        if (input.isNotBlank()) {
            vm.sendCommand(input) { input = "" }
        }
    }

    // Online player names that complete the word being typed (after the command).
    val lastWord = input.substringAfterLast(' ')
    val playerSuggestions = if (' ' in input) {
        vm.stats?.players.orEmpty().filter { it.startsWith(lastWord, ignoreCase = true) && !it.equals(lastWord, ignoreCase = true) }
    } else {
        emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = follow, onClick = { follow = !follow }, label = { Text(stringResource(R.string.follow)) })
                LogLevel.entries.forEach { l ->
                    FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l.label) })
                }
            }
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, contentDescription = if (searching) stringResource(R.string.close_search) else stringResource(R.string.search))
            }
        }
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_the_console)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                supportingText = { Text(stringResource(R.string.x_of_x_lines, shown.size, vm.logs.size)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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
                itemsIndexed(shown) { _, line ->
                    Text(
                        line,
                        color = lineColor(line),
                        fontFamily = Mono,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    clipboard.copy(line)
                                    vm.notify(str(R.string.line_copied))
                                },
                            ),
                    )
                }
                if (shown.isEmpty() && vm.logs.isNotEmpty()) {
                    item { Text(stringResource(R.string.no_lines_match), color = ConsoleDim, fontSize = 12.sp) }
                }
            }
        }
        Text(
            stringResource(R.string.long_press_a_line_to_copy),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 6.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (playerSuggestions.isNotEmpty()) {
                items(playerSuggestions) { name ->
                    SuggestionChip(
                        onClick = { input = input.substringBeforeLast(' ') + " " + name + " " },
                        label = { Text(name) },
                        icon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            } else {
                val chips = (vm.recentCommands + presetCommands).distinct()
                items(chips) { cmd ->
                    SuggestionChip(
                        onClick = { input = cmd },
                        label = { Text(cmd.trim(), fontFamily = Mono) },
                    )
                }
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
                placeholder = { Text(stringResource(R.string.command_without)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
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
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
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
    val activity = LocalContext.current.findFragmentActivity()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            TabHeader(stringResource(R.string.backup_profiles), state.loading) {
                vm.loadBackups()
                vm.loadTasks()
            }
        }
        state.error?.let { item(key = "error") { ErrorBanner(it, onRetry = vm::loadBackups) } }

        val list = state.data
        if (list != null && list.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    title = stringResource(R.string.no_backup_profiles_found),
                    body = stringResource(R.string.you_can_still_trigger_the_server),
                    action = stringResource(R.string.back_up_now),
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
                stringResource(R.string.backups_run_on_the_crafty_host),
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
            onRestore = { name, inPlace ->
                verifyUser(activity, str(R.string.restore_x, name), str(R.string.the_server_is_stopped_first), onSuccess = {
                    vm.restoreBackup(b, name, inPlace) { restoring = null }
                })
            },
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                            stringResource(R.string.default_label),
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
            b.status?.let { status ->
                Row {
                    Text(
                        stringResource(R.string.last_result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(120.dp),
                    )
                    Text(
                        listOfNotNull(status, b.statusMessage).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (b.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Text(stringResource(R.string.schedule), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
                schedulesLoading -> Text(stringResource(R.string.loading_schedule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                schedulesError != null -> Text(stringResource(R.string.couldn_t_load_schedules_x, schedulesError), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else -> Text(
                    stringResource(R.string.not_scheduled_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAddSchedule, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_schedule))
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings))
                }
            }
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.restore_a_backup))
            }
            FilledTonalButton(onClick = onRun, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.back_up_now))
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
        item(key = "header") { TabHeader(stringResource(R.string.scheduled_tasks), state.loading, vm::loadTasks) }
        item(key = "new") {
            FilledTonalButton(onClick = { editing = TaskEdit(null, null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.new_schedule))
            }
        }
        state.error?.let { item(key = "error") { ErrorBanner(it, onRetry = vm::loadTasks) } }

        val list = state.data
        if (list != null && list.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    title = stringResource(R.string.no_scheduled_tasks),
                    body = stringResource(R.string.tap_new_schedule_to_restart_back),
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
            title = { Text(stringResource(R.string.run_x_now, t.name)) },
            text = { Text(t.command?.let { "${t.actionLabel}: $it" } ?: t.actionLabel) },
            confirmButton = { TextButton(onClick = { confirmRun = null; vm.runTaskNow(t) }) { Text(stringResource(R.string.run_now)) } },
            dismissButton = { TextButton(onClick = { confirmRun = null }) { Text(stringResource(R.string.cancel)) } },
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                    fontFamily = if (t.command != null) Mono else FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRun) { Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.run_now)) }
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
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh)) }
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
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
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
enum class ManagePage(val route: String, @StringRes private val titleRes: Int, @StringRes private val subtitleRes: Int, val icon: ImageVector) {
    PROPERTIES("properties", R.string.page_properties, R.string.page_properties_sub, Icons.Filled.Tune),
    PLAYERS("players", R.string.page_players, R.string.page_players_sub, Icons.Filled.Group),
    OPTIONS("options", R.string.page_options, R.string.page_options_sub, Icons.Filled.Settings),
    ADDONS("addons", R.string.page_addons, R.string.page_addons_sub, Icons.Filled.Extension),
    LOGS("logs", R.string.page_logs, R.string.page_logs_sub, Icons.Filled.Description);

    val title: String get() = str(titleRes)
    val subtitle: String get() = str(subtitleRes)
}

@Composable
private fun RestoreDialog(
    config: BackupConfig,
    files: com.vknxworks.craftyctrl.ui.Loadable<List<String>>,
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
        title = { Text(stringResource(R.string.restore_from_x, config.name)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    files.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    list.isNotEmpty() -> {
                        Text(stringResource(R.string.pick_a_backup), style = MaterialTheme.typography.labelLarge)
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
                            stringResource(R.string.crafty_doesn_t_list_backup_files),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it },
                            label = { Text(stringResource(R.string.backup_file_name)) },
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
                        Text(stringResource(R.string.restore_in_place), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (inPlace) stringResource(R.string.backup_files_overwrite_the_current_ones)
                            else stringResource(R.string.the_server_folder_is_cleared_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    if (running) stringResource(R.string.restore_warning_running) else stringResource(R.string.everything_since_this_backup_is_lost),
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
                Text(stringResource(R.string.restore), color = if (name.isNotEmpty() && armed && !busy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CountdownBanner(c: Countdown, onCancel: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(c) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }
    val left = ((c.endsAt - now) / 1000).coerceAtLeast(0)
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(10.dp))
            Text(
                (if (c.action == PowerAction.RESTART) stringResource(R.string.restarting_in) else stringResource(R.string.stopping_in)) + "%d:%02d".format(left / 60, left % 60),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = Mono,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
        Text(
            stringResource(R.string.players_get_chat_warnings_as_it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Confirmation for restart, stop and kill; restart and stop can warn players first. */
@Composable
private fun PowerDialog(
    action: PowerAction,
    serverName: String,
    onDismiss: () -> Unit,
    onConfirm: (delaySeconds: Int, note: String?) -> Unit,
) {
    val canWarn = action == PowerAction.RESTART || action == PowerAction.STOP
    var delay by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${action.label} ${serverName.ifBlank { "server" }}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(action.confirmText.orEmpty())
                if (canWarn) {
                    Text(stringResource(R.string.when_label), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to stringResource(R.string.now)) + listOf(1, 5, 10).map { it * 60 to stringResource(R.string.x_min, it) }.forEach { (sec, label) ->
                            FilterChip(selected = delay == sec, onClick = { delay = sec }, label = { Text(label) })
                        }
                    }
                    if (delay > 0) {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text(stringResource(R.string.reason_optional)) },
                            placeholder = { Text(stringResource(R.string.e_g_updating_plugins)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.players_are_warned_in_chat_at),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    stringResource(R.string.you_ll_confirm_with_your_fingerprint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(delay, note.trim().ifEmpty { null }) }) {
                Text(
                    if (delay > 0) stringResource(R.string.warn_x, action.label.lowercase()) else action.label,
                    color = if (action.dangerous || action == PowerAction.STOP) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
