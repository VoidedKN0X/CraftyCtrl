@file:OptIn(ExperimentalMaterial3Api::class)

package com.vknxworks.craftyctrl.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import com.vknxworks.craftyctrl.ui.components.CtrlBottomBarSpace
import com.vknxworks.craftyctrl.ui.components.CtrlHeroCard
import com.vknxworks.craftyctrl.ui.components.CtrlIconButton
import com.vknxworks.craftyctrl.ui.components.CtrlListRow
import com.vknxworks.craftyctrl.ui.components.CtrlMainTopBar
import com.vknxworks.craftyctrl.ui.components.CtrlMetricCard
import com.vknxworks.craftyctrl.ui.components.HeroBars
import com.vknxworks.craftyctrl.ui.components.HeroNumber
import com.vknxworks.craftyctrl.ui.theme.CtrlColors
import com.vknxworks.craftyctrl.ui.theme.CtrlType
import com.vknxworks.craftyctrl.ui.components.CtrlDialog
import com.vknxworks.craftyctrl.ui.components.CtrlTextField
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Memory
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
import com.vknxworks.craftyctrl.ui.CtrlCard
import com.vknxworks.craftyctrl.ui.CtrlHeader
import com.vknxworks.craftyctrl.ui.EmptyState
import com.vknxworks.craftyctrl.ui.ErrorBanner
import com.vknxworks.craftyctrl.ui.Meter
import com.vknxworks.craftyctrl.ui.PollWhileVisible
import com.vknxworks.craftyctrl.ui.ProfileButton
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
import androidx.compose.ui.res.stringResource
import com.vknxworks.craftyctrl.R
import androidx.compose.ui.res.pluralStringResource
import com.vknxworks.craftyctrl.str

/** Home: Servers and Host as bottom tabs, like the other Ctrl apps. */
@Composable
fun ServerListScreen(
    api: CraftyApi,
    username: String?,
    onOpen: (ServerSummary) -> Unit,
    onYou: () -> Unit,
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
    val islandSpace = navBottom + CtrlBottomBarSpace
    // A Surface gives text and icons the theme's content colour (light text in dark mode, dark in light).
    Surface(color = CtrlColors.Background, contentColor = CtrlColors.Text) {
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
                        onBroadcast = { broadcastOpen = true }, username = username, onYou = onYou,
                    )
                    else -> HostTab(vm, host, islandSpace, username, onYou)
                }
            }
        }
        if (tab == 0) {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_server)) },
                shape = RoundedCornerShape(18.dp),
                containerColor = CtrlColors.Accent,
                contentColor = CtrlColors.OnAccent,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = islandSpace + 12.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = islandSpace))
        TabIsland(
            tabs = listOf(
                IslandTab(0, if (tab == 0) Icons.Filled.Dns else Icons.Outlined.Dns, stringResource(R.string.servers)),
                IslandTab(1, if (tab == 1) Icons.Filled.Memory else Icons.Outlined.Memory, stringResource(R.string.host)),
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
        CtrlDialog(
            onDismissRequest = { if (!vm.broadcasting) broadcastOpen = false },
            icon = { Icon(Icons.Outlined.Campaign, contentDescription = null) },
            title = { Text(stringResource(R.string.broadcast)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.sends_a_chat_message_to_every, running),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CtrlTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.message)) },
                        enabled = !vm.broadcasting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.broadcast(text) { broadcastOpen = false } }, enabled = text.isNotBlank() && !vm.broadcasting && running > 0) {
                    Text(stringResource(R.string.send))
                }
            },
            dismissButton = { TextButton(onClick = { broadcastOpen = false }, enabled = !vm.broadcasting) { Text(stringResource(R.string.cancel)) } },
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
    username: String?,
    onYou: () -> Unit,
) {
    val bars = WindowInsets.statusBars.asPaddingValues()
    val running = vm.rows.count { it.stats?.running == true }
    val notEncrypted = remember(vm) { com.vknxworks.craftyctrl.data.CleartextPolicy.isHttp(vm.baseUrl) }

    PullToRefreshBox(
        isRefreshing = vm.refreshing,
        onRefresh = vm::refreshNow,
        modifier = Modifier.fillMaxSize().background(CtrlColors.Background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = bars.calculateTopPadding() + 8.dp,
                bottom = bottomPadding + 88.dp,
            ),
        ) {
            item(key = "topbar") {
                CtrlMainTopBar("Crafty") {
                    CtrlIconButton(Icons.Outlined.Campaign, stringResource(R.string.broadcast), onClick = onBroadcast)
                    ProfileButton(username, onYou)
                }
            }
            item(key = "header") {
                CtrlHeader(
                    icon = Icons.Outlined.Dns,
                    label = stringResource(if (vm.error != null && vm.rows.isEmpty()) R.string.signal_panel_offline else R.string.signal_panel_online),
                    title = stringResource(R.string.servers),
                    subtitle = listOfNotNull(
                        host,
                        stringResource(R.string.not_encrypted_http).takeIf { notEncrypted },
                        vm.rows.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.count_servers, it.size, it.size) },
                    ).joinToString(" · "),
                    modifier = Modifier.padding(top = 14.dp, bottom = 22.dp),
                )
            }
            when {
                vm.error != null && vm.rows.isEmpty() -> item(key = "error") {
                    EmptyState(stringResource(R.string.can_t_reach_crafty), vm.error!!, stringResource(R.string.retry), onAction = vm::refreshNow)
                }
                vm.loading && vm.rows.isEmpty() -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(320.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                vm.rows.isEmpty() -> item(key = "empty") {
                    EmptyState(stringResource(R.string.no_servers_yet), stringResource(R.string.this_account_can_t_see_any), icon = Icons.Outlined.Dns)
                }
                else -> {
                    item(key = "overview") { ServersOverview(vm, Modifier.padding(bottom = 22.dp)) }
                    vm.error?.let { message -> item(key = "error-banner") { ErrorBanner(message, Modifier.padding(bottom = 12.dp), onRetry = vm::refreshNow) } }
                    item(key = "list-head") {
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.servers), style = MaterialTheme.typography.titleMedium, color = CtrlColors.Text, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.signal_x_of_y_running, running, vm.rows.size).uppercase(), style = CtrlType.MonoStatus, color = CtrlColors.Muted)
                        }
                    }
                    items(vm.rows, key = { it.summary.id }) { row ->
                        ServerCard(row, Modifier.animateItem()) { onOpen(row.summary) }
                    }
                }
            }
        }
    }
}

/** Hero card with players online and panel CPU, then memory and storage cards (as in the ProxCtrl overview). */
@Composable
private fun ServersOverview(vm: ServerListViewModel, modifier: Modifier = Modifier) {
    val live = vm.rows.mapNotNull { it.stats?.takeIf { s -> s.running } }
    val players = live.sumOf { it.online ?: 0 }
    val maxPlayers = live.sumOf { it.max ?: 0 }
    val h = vm.host
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CtrlHeroCard(
            label = stringResource(R.string.players_online_2),
            trailingLabel = h?.cpuPercent?.let { "CPU " + "%.0f%%".format(it) },
        ) {
            HeroNumber("$players", unit = if (maxPlayers > 0) " / $maxPlayers" else null)
            if (vm.hostCpuSamples.size >= 2) HeroBars(vm.hostCpuSamples)
        }
        if (h != null && (h.memPercent != null || h.disks.isNotEmpty())) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                h.memPercent?.let { mp ->
                    CtrlMetricCard(
                        stringResource(R.string.memory),
                        h.memUsed ?: "%.0f".format(mp),
                        Modifier.weight(1f),
                        unit = h.memTotal?.let { " / $it" } ?: if (h.memUsed == null) "%" else null,
                        fraction = (mp / 100).toFloat(),
                    )
                }
                h.disks.firstOrNull()?.let { d ->
                    CtrlMetricCard(
                        stringResource(R.string.storage),
                        d.used ?: d.percent?.let { "%.0f".format(it) } ?: "—",
                        Modifier.weight(1f),
                        unit = d.total?.let { " / $it" } ?: if (d.used == null && d.percent != null) "%" else null,
                        fraction = d.percent?.let { (it / 100).toFloat() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerCard(row: ServerRow, modifier: Modifier, onClick: () -> Unit) {
    val s = row.stats
    val live = s != null && (s.state == ServerState.RUNNING || s.state == ServerState.STARTING)
    val meta = listOfNotNull(
        s?.version ?: prettyType(row.summary.type),
        when {
            s == null -> stringResource(R.string.stats_unavailable)
            live && s.playersKnown -> stringResource(R.string.players) + " " + playersText(s.online, s.max)
            live -> s.cpu?.let { "CPU " + "%.0f%%".format(it) }
            else -> null
        },
        if (live) s?.mem?.let { it.replace(" ", "") } else null,
    ).joinToString(" · ")
    CtrlListRow(
        title = row.summary.name,
        meta = row.summary.port?.let { ":$it" } ?: row.summary.id.take(4),
        subtitle = meta,
        status = s?.state?.label ?: stringResource(R.string.unknown),
        statusColor = stateColor(s?.state),
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
private fun HostTab(vm: ServerListViewModel, host: String, bottomPadding: Dp, username: String?, onYou: () -> Unit) {
    val h = vm.host
    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
            CtrlMainTopBar("Crafty") { ProfileButton(username, onYou) }
            CtrlHeader(
                icon = Icons.Outlined.Memory,
                label = stringResource(R.string.signal_host_live),
                title = stringResource(R.string.host),
                subtitle = when {
                    vm.hostError != null && h != null -> stringResource(R.string.offline_showing_last_data)
                    h == null && vm.hostError != null -> host
                    h == null -> stringResource(R.string.loading)
                    else -> listOfNotNull(host, h.cpuCores?.let { pluralStringResource(R.plurals.cpu_cores, it, it) }, stringResource(R.string.live)).joinToString(" · ")
                },
                subtitleColor = if (vm.hostError != null && h != null) CtrlColors.Error else CtrlColors.Muted,
                modifier = Modifier.padding(top = 14.dp, bottom = 22.dp),
            )
        }
        if (h == null) {
            Box(Modifier.fillMaxSize().padding(bottom = bottomPadding), Alignment.Center) {
                if (vm.hostError != null) EmptyState(stringResource(R.string.host_stats_unavailable), vm.hostError!!, icon = Icons.Outlined.Memory)
                else CircularProgressIndicator()
            }
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = bottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HostOverview(vm, h)
            CtrlCard(stringResource(R.string.system)) {
                Meter("CPU", h.cpuPercent, h.cpuPercent?.let { "%.0f %%".format(it) + (h.cpuFreq?.let { f -> " · $f" } ?: "") })
                Meter(stringResource(R.string.memory), h.memPercent, usedOf(h.memUsed, h.memTotal))
            }
            if (h.disks.isNotEmpty()) {
                CtrlCard(stringResource(R.string.storage)) {
                    h.disks.forEach { d ->
                        Meter(d.mount, d.percent, usedOf(d.used, d.total))
                    }
                }
            }
            h.bootTime?.let { boot ->
                CtrlCard(stringResource(R.string.machine)) {
                    StatRow(stringResource(R.string.up_since), boot.substringBefore('.'))
                    parseTimestamp(boot)?.let { t ->
                        val days = (System.currentTimeMillis() - t) / 86_400_000
                        StatRow(stringResource(R.string.uptime), if (days > 0) pluralStringResource(R.plurals.x_days, days.toInt(), days.toInt()) else stringResource(R.string.under_a_day))
                    }
                }
            }
        }
    }
}

/** CPU as the hero, then servers running and players online as metric cards. */
@Composable
private fun HostOverview(vm: ServerListViewModel, h: HostStats) {
    val running = vm.rows.count { it.stats?.running == true }
    val players = vm.rows.sumOf { r -> r.stats?.takeIf { it.running }?.online ?: 0 }
    CtrlHeroCard(
        label = stringResource(R.string.signal_cpu_load),
        trailingLabel = h.cpuCores?.let { pluralStringResource(R.plurals.cpu_cores, it, it) },
    ) {
        HeroNumber(h.cpuPercent?.let { "%.0f".format(it) } ?: "—", unit = if (h.cpuPercent != null) "%" else null)
        if (vm.hostCpuSamples.size >= 2) HeroBars(vm.hostCpuSamples)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CtrlMetricCard(stringResource(R.string.servers_running), "$running", Modifier.weight(1f), unit = " / ${vm.rows.size}")
        CtrlMetricCard(stringResource(R.string.players_online_2), "$players", Modifier.weight(1f))
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
/** "3.1 GB of 8 GB", or whichever of the two is known. */
@Composable
private fun usedOf(used: String?, total: String?): String? = when {
    used != null && total != null -> stringResource(R.string.x_of_x, used, total)
    else -> used ?: total
}

internal fun uptimeShort(started: String): String? {
    val t = parseTimestamp(started) ?: return null
    val mins = ((System.currentTimeMillis() - t) / 60_000).coerceAtLeast(0)
    val d = mins / 1440
    val h = (mins % 1440) / 60
    val m = mins % 60
    return when {
        d > 0 -> str(R.string.short_d_h, d, h)
        h > 0 -> str(R.string.short_h_m, h, m)
        else -> str(R.string.short_m, m)
    }
}
