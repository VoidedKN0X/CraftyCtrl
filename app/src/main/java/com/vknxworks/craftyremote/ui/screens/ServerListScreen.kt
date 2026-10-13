@file:OptIn(ExperimentalMaterial3Api::class)

package com.vknxworks.craftyremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.PhoneAndroid
import com.vknxworks.craftyremote.ui.AccentCard
import com.vknxworks.craftyremote.ui.RingGauge
import com.vknxworks.craftyremote.ui.ratio
import com.vknxworks.craftyremote.ui.ChartPlayers
import com.vknxworks.craftyremote.ui.theme.stateColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vknxworks.craftyremote.data.CraftyApi
import com.vknxworks.craftyremote.data.HostStats
import com.vknxworks.craftyremote.ui.ChartCpu
import com.vknxworks.craftyremote.ui.ChartMem
import com.vknxworks.craftyremote.ui.LineChart
import com.vknxworks.craftyremote.ui.UsageBar
import com.vknxworks.craftyremote.data.ServerState
import com.vknxworks.craftyremote.data.ServerSummary
import com.vknxworks.craftyremote.ui.ErrorBanner
import com.vknxworks.craftyremote.ui.PollWhileVisible
import com.vknxworks.craftyremote.ui.ServerListViewModel
import com.vknxworks.craftyremote.ui.ServerRow
import com.vknxworks.craftyremote.ui.StatusPill
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    api: CraftyApi,
    onOpen: (ServerSummary) -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    val vm: ServerListViewModel = viewModel(factory = viewModelFactory { initializer { ServerListViewModel(api) } })
    PollWhileVisible(api, 5_000) { vm.refresh() }

    val host = remember(api.baseUrl) { runCatching { URI(api.baseUrl).host }.getOrNull() ?: api.baseUrl }
    var menuOpen by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    Column {
                        Text("Servers", fontWeight = FontWeight.Bold)
                        Text(
                            host,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onSettings()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onSignOut()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = vm::refreshNow,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                vm.error?.let { message ->
                    item(key = "error") { ErrorBanner(message, onRetry = vm::refreshNow) }
                }
                vm.host?.let { h ->
                    item(key = "host") { HostCard(h, vm.hostCpuSamples, vm.hostMemSamples) }
                }
                when {
                    vm.loading && vm.rows.isEmpty() -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    vm.rows.isEmpty() && vm.error == null -> item(key = "empty") {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.Dns, contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("No servers yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "This account can't see any servers. Check its role in Crafty.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> items(vm.rows, key = { it.summary.id }) { row ->
                        ServerCard(row, onClick = { onOpen(row.summary) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(row: ServerRow, onClick: () -> Unit) {
    val s = row.stats
    val live = s != null && (s.state == ServerState.RUNNING || s.state == ServerState.STARTING)
    AccentCard(accent = stateColor(s?.state), onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (row.summary.isBedrock) Icons.Filled.PhoneAndroid else Icons.Filled.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.summary.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(prettyType(row.summary.type), s?.version).joinToString(" · ")
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(s?.state)
            }

            when {
                s == null -> Text(
                    "Stats unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                live -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RingGauge(s.cpu?.let { (it / 100).toFloat() }, s.cpu?.let { "%.0f%%".format(it) } ?: "—", "CPU", ChartCpu, size = 64.dp, stroke = 7.dp)
                    RingGauge(s.memPercent?.let { (it / 100).toFloat() }, s.memPercent?.let { "%.0f%%".format(it) } ?: "—", s.mem ?: "Memory", ChartMem, size = 64.dp, stroke = 7.dp)
                    if (s.playersKnown) {
                        RingGauge(ratio(s.online, s.max), s.online?.toString() ?: "—", "Players", ChartPlayers, size = 64.dp, stroke = 7.dp)
                    }
                }
            }
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

@Composable
private fun HostCard(h: HostStats, cpu: List<Float>, mem: List<Float>) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Host machine", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val cpuInfo = listOfNotNull(h.cpuCores?.let { "$it cores" }, h.cpuFreq).joinToString(" · ")
                if (cpuInfo.isNotEmpty()) {
                    Text(cpuInfo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HostMetric(
                    "CPU", h.cpuPercent?.let { "%.0f%%".format(it) } ?: "—", cpu, ChartCpu,
                    null, Modifier.weight(1f),
                )
                HostMetric(
                    "RAM", h.memPercent?.let { "%.0f%%".format(it) } ?: "—", mem, ChartMem,
                    listOfNotNull(h.memUsed, h.memTotal).joinToString(" / ").ifEmpty { null }, Modifier.weight(1f),
                )
            }
            h.disks.take(4).forEach { d ->
                UsageBar(d.mount, d.percent, listOfNotNull(d.used, d.total).joinToString(" / ").ifEmpty { null })
            }
        }
    }
}

@Composable
private fun HostMetric(
    label: String,
    value: String,
    samples: List<Float>,
    color: androidx.compose.ui.graphics.Color,
    detail: String?,
    modifier: Modifier,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        }
        LineChart(samples.map { it }, color, maxY = 100f, height = 44.dp, gridLines = 0)
        detail?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
