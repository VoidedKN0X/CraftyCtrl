@file:OptIn(ExperimentalMaterial3Api::class)

package com.vknxworks.craftyctrl.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.HostStats
import com.vknxworks.craftyctrl.data.ServerState
import com.vknxworks.craftyctrl.data.ServerSummary
import com.vknxworks.craftyctrl.data.parseTimestamp
import com.vknxworks.craftyctrl.ui.BigStat
import com.vknxworks.craftyctrl.ui.CtrlCard
import com.vknxworks.craftyctrl.ui.CtrlHeader
import com.vknxworks.craftyctrl.ui.Dot
import com.vknxworks.craftyctrl.ui.EmptyState
import com.vknxworks.craftyctrl.ui.ErrorBanner
import com.vknxworks.craftyctrl.ui.Meter
import com.vknxworks.craftyctrl.ui.PollWhileVisible
import com.vknxworks.craftyctrl.ui.ServerListViewModel
import com.vknxworks.craftyctrl.ui.ServerRow
import com.vknxworks.craftyctrl.ui.StatRow
import com.vknxworks.craftyctrl.ui.rememberRefreshMs
import com.vknxworks.craftyctrl.ui.theme.stateColor
import java.net.URI
import com.vknxworks.craftyctrl.ui.IslandTab
import com.vknxworks.craftyctrl.ui.TabIsland
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** Home: Servers and Host as bottom tabs, like the other Ctrl apps. */
@Composable
fun ServerListScreen(
    api: CraftyApi,
    onOpen: (ServerSummary) -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
    onCreate: () -> Unit,
) {
    val vm: ServerListViewModel = viewModel(factory = viewModelFactory { initializer { ServerListViewModel(api) } })
    // The list asks for every server at once, so it refreshes a little less often than one server.
    val refreshMs = rememberRefreshMs().coerceAtLeast(3_000)
    PollWhileVisible(api to refreshMs, refreshMs) { vm.refresh() }

    val host = remember(api.baseUrl) { runCatching { URI(api.baseUrl).host }.getOrNull() ?: api.baseUrl }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var broadcastOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { for (m in vm.messages) snackbar.showSnackbar(m) }
    // Back on the Host tab returns to Servers first.
    if (tab != 0) BackHandler { tab = 0 }

    // Content runs edge to edge under the floating island, which blurs it.
    val haze = remember { HazeState() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val islandSpace = navBottom + 78.dp
    // A Surface gives text and icons the theme's content colour (light text in dark mode, dark in light).
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "homeTab",
            ) { t ->
                when (t) {
                    0 -> ServersTab(
                        vm, host, islandSpace, onOpen,
                        onBroadcast = { broadcastOpen = true }, onSettings = onSettings, onSignOut = onSignOut,
                    )
                    else -> HostTab(vm, host, islandSpace)
                }
            }
        }
        if (tab == 0) {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New server") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = islandSpace + 12.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = islandSpace))
        TabIsland(
            tabs = listOf(
                IslandTab(0, if (tab == 0) Icons.Filled.Dns else Icons.Outlined.Dns, "Servers"),
                IslandTab(1, if (tab == 1) Icons.Filled.Memory else Icons.Outlined.Memory, "Host"),
            ),
            selected = tab,
            hazeState = haze,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    }

    if (broadcastOpen) {
        var text by remember { mutableStateOf("") }
        val running = vm.rows.count { it.stats?.running == true }
        AlertDialog(
            onDismissRequest = { if (!vm.broadcasting) broadcastOpen = false },
            icon = { Icon(Icons.Outlined.Campaign, contentDescription = null) },
            title = { Text("Broadcast") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Sends a chat message to every running server ($running now).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Message") },
                        enabled = !vm.broadcasting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.broadcast(text) { broadcastOpen = false } }, enabled = text.isNotBlank() && !vm.broadcasting && running > 0) {
                    Text("Send")
                }
            },
            dismissButton = { TextButton(onClick = { broadcastOpen = false }, enabled = !vm.broadcasting) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ServersTab(
    vm: ServerListViewModel,
    host: String,
    bottomPadding: Dp,
    onOpen: (ServerSummary) -> Unit,
    onBroadcast: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val bars = WindowInsets.statusBars.asPaddingValues()
    val running = vm.rows.count { it.stats?.running == true }

    PullToRefreshBox(
        isRefreshing = vm.refreshing,
        onRefresh = vm::refreshNow,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = bars.calculateTopPadding() + 8.dp,
                bottom = bottomPadding + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                CtrlHeader(
                    icon = Icons.Outlined.Dns,
                    title = "Servers",
                    subtitle = listOfNotNull(
                        host,
                        vm.rows.takeIf { it.isNotEmpty() }?.let { "${it.size} servers" },
                        vm.rows.takeIf { it.isNotEmpty() }?.let { "$running running" },
                    ).joinToString(" · "),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Box {
                        FilledTonalIconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Broadcast") },
                                leadingIcon = { Icon(Icons.Outlined.Campaign, contentDescription = null) },
                                onClick = { menuOpen = false; onBroadcast() },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                onClick = { menuOpen = false; onSettings() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                                onClick = { menuOpen = false; onSignOut() },
                            )
                        }
                    }
                }
            }
            when {
                vm.error != null && vm.rows.isEmpty() -> item(key = "error") {
                    EmptyState("Can't reach Crafty", vm.error!!, "Retry", onAction = vm::refreshNow)
                }
                vm.loading && vm.rows.isEmpty() -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(320.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                vm.rows.isEmpty() -> item(key = "empty") {
                    EmptyState("No servers yet", "This account can't see any servers. Check its role in Crafty, or create one.", icon = Icons.Outlined.Dns)
                }
                else -> {
                    vm.error?.let { message -> item(key = "error-banner") { ErrorBanner(message, onRetry = vm::refreshNow) } }
                    items(vm.rows, key = { it.summary.id }) { row ->
                        ServerCard(row, Modifier.animateItem()) { onOpen(row.summary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(row: ServerRow, modifier: Modifier, onClick: () -> Unit) {
    val s = row.stats
    val live = s != null && (s.state == ServerState.RUNNING || s.state == ServerState.STARTING)
    CtrlCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    row.summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(s?.version ?: prettyType(row.summary.type), row.summary.port?.let { ":$it" }).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(stateColor(s?.state))
                Spacer(Modifier.width(6.dp))
                Text(
                    s?.state?.label ?: "Unknown",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            s == null -> Text("Stats unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            live -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("CPU", s.cpu?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
                BigStat("Memory", s.mem?.replace(" ", "") ?: "—", Modifier.weight(1f))
                BigStat(
                    if (s.playersKnown) "Players" else "Uptime",
                    if (s.playersKnown) playersText(s.online, s.max).replace(" ", "") else s.started?.let { uptimeShort(it) } ?: "—",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HostTab(vm: ServerListViewModel, host: String, bottomPadding: Dp) {
    val h = vm.host
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(cs.background)) {
        CtrlHeader(
            icon = Icons.Outlined.Memory,
            title = "Host",
            subtitle = when {
                vm.hostError != null && h != null -> "Offline · showing last data"
                h == null && vm.hostError != null -> host
                h == null -> "Loading…"
                else -> listOfNotNull(host, h.cpuCores?.let { "$it cores" }, "live").joinToString(" · ")
            },
            subtitleColor = if (vm.hostError != null && h != null) cs.error else cs.onSurfaceVariant,
            modifier = Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        if (h == null) {
            Box(Modifier.fillMaxSize().padding(bottom = bottomPadding), Alignment.Center) {
                if (vm.hostError != null) EmptyState("Host stats unavailable", vm.hostError!!, icon = Icons.Outlined.Memory)
                else CircularProgressIndicator()
            }
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 4.dp, bottom = bottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HostOverview(vm, h)
            CtrlCard("System") {
                Meter("CPU", h.cpuPercent, h.cpuPercent?.let { "%.0f %%".format(it) + (h.cpuFreq?.let { f -> " · $f" } ?: "") })
                Meter("Memory", h.memPercent, listOfNotNull(h.memUsed, h.memTotal).joinToString(" of ").ifEmpty { null })
            }
            if (h.disks.isNotEmpty()) {
                CtrlCard("Storage") {
                    h.disks.forEach { d ->
                        Meter(d.mount, d.percent, listOfNotNull(d.used, d.total).joinToString(" of ").ifEmpty { null })
                    }
                }
            }
            h.bootTime?.let { boot ->
                CtrlCard("Machine") {
                    StatRow("Up since", boot.substringBefore('.'))
                    parseTimestamp(boot)?.let { t ->
                        val days = (System.currentTimeMillis() - t) / 86_400_000
                        StatRow("Uptime", if (days > 0) "$days days" else "under a day")
                    }
                }
            }
        }
    }
}

@Composable
private fun HostOverview(vm: ServerListViewModel, h: HostStats) {
    val running = vm.rows.count { it.stats?.running == true }
    val players = vm.rows.sumOf { r -> r.stats?.takeIf { it.running }?.online ?: 0 }
    CtrlCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigStat("Servers running", "$running/${vm.rows.size}", Modifier.weight(1f))
            BigStat("Players online", "$players", Modifier.weight(1f))
            BigStat("CPU", h.cpuPercent?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
        }
    }
}

internal fun playersText(online: Int?, max: Int?): String = when {
    online == null -> "—"
    max == null || max <= 0 -> "$online"
    else -> "$online / $max"
}

internal fun prettyType(type: String): String? = when (type) {
    "minecraft-java" -> "Java"
    "minecraft-bedrock" -> "Bedrock"
    "" -> null
    else -> type.replace('-', ' ').replaceFirstChar { it.uppercase() }
}

/** "2d 4h", "6h 12m", "8m" since [started], or null if it can't be read. */
internal fun uptimeShort(started: String): String? {
    val t = parseTimestamp(started) ?: return null
    val mins = ((System.currentTimeMillis() - t) / 60_000).coerceAtLeast(0)
    val d = mins / 1440
    val h = (mins % 1440) / 60
    val m = mins % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}
