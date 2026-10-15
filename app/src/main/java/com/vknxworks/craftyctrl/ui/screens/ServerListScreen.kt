@file:OptIn(ExperimentalMaterial3Api::class)

package com.vknxworks.craftyctrl.ui.screens

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
import com.vknxworks.craftyctrl.ui.AccentCard
import com.vknxworks.craftyctrl.ui.RingGauge
import com.vknxworks.craftyctrl.ui.ratio
import com.vknxworks.craftyctrl.ui.ChartPlayers
import com.vknxworks.craftyctrl.ui.theme.stateColor
import com.vknxworks.craftyctrl.ui.rememberRefreshMs
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.HostStats
import com.vknxworks.craftyctrl.ui.ChartCpu
import com.vknxworks.craftyctrl.ui.ChartMem
import com.vknxworks.craftyctrl.ui.LineChart
import com.vknxworks.craftyctrl.ui.UsageBar
import com.vknxworks.craftyctrl.data.ServerState
import com.vknxworks.craftyctrl.data.ServerSummary
import com.vknxworks.craftyctrl.ui.ErrorBanner
import com.vknxworks.craftyctrl.ui.PollWhileVisible
import com.vknxworks.craftyctrl.ui.ServerListViewModel
import com.vknxworks.craftyctrl.ui.ServerRow
import com.vknxworks.craftyctrl.ui.StatusPill
import java.net.URI
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import com.vknxworks.craftyctrl.data.parseTimestamp
import com.vknxworks.craftyctrl.ui.Caption
import com.vknxworks.craftyctrl.ui.Dot
import com.vknxworks.craftyctrl.ui.HairBar
import com.vknxworks.craftyctrl.ui.theme.GeistMono

@OptIn(ExperimentalMaterial3Api::class)
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
    var menuOpen by remember { mutableStateOf(false) }
    var broadcastOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { for (m in vm.messages) snackbar.showSnackbar(m) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            OutlinedButton(
                onClick = onCreate,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(horizontal = 22.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New server", style = MaterialTheme.typography.bodyLarge)
            }
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
                contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 112.dp),
            ) {
                item(key = "header") {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 28.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(host, style = MaterialTheme.typography.labelMedium, fontFamily = GeistMono, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text("Servers", style = MaterialTheme.typography.headlineLarge)
                        }
                        IconButton(onClick = { broadcastOpen = true }) {
                            Icon(Icons.Outlined.Campaign, contentDescription = "Broadcast to all servers")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                    }
                }
                vm.error?.let { message ->
                    item(key = "error") { ErrorBanner(message, Modifier.padding(bottom = 16.dp), onRetry = vm::refreshNow) }
                }
                vm.host?.let { h ->
                    item(key = "host") { HostRow(h, Modifier.padding(bottom = 32.dp)) }
                }
                when {
                    vm.loading && vm.rows.isEmpty() -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }

                    vm.rows.isEmpty() && vm.error == null -> item(key = "empty") {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("No servers yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "This account can't see any servers. Check its role in Crafty.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> {
                        item(key = "list-head") {
                            val running = vm.rows.count { it.stats?.running == true }
                            Column {
                                Row(Modifier.padding(bottom = 12.dp)) {
                                    Caption("$running of ${vm.rows.size} running", Modifier.weight(1f))
                                    Caption("Players")
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        items(vm.rows, key = { it.summary.id }) { row ->
                            ServerLine(row, onClick = { onOpen(row.summary) })
                        }
                    }
                }
            }
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
private fun ServerLine(row: ServerRow, onClick: () -> Unit) {
    val s = row.stats
    val live = s != null && (s.state == ServerState.RUNNING || s.state == ServerState.STARTING)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(stateColor(s?.state))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(row.summary.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(
                        s?.version ?: prettyType(row.summary.type),
                        if (live) row.summary.port?.let { ":$it" } else s?.state?.label,
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (live && s?.playersKnown == true) playersText(s.online, s.max).replace(" ", "") else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GeistMono,
                )
            }
            if (live && s != null) {
                Row(Modifier.padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    listOfNotNull(
                        s.cpu?.let { "CPU %.0f%%".format(it) },
                        s.mem?.let { "RAM $it" },
                        s.started?.let { uptimeShort(it) }?.let { "Up $it" },
                    ).forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = GeistMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (s == null) {
                Text("Stats unavailable", Modifier.padding(start = 20.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
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
private fun HostRow(h: HostStats, modifier: Modifier = Modifier) {
    // The root filesystem if Crafty lists it, otherwise the first disk.
    val disk = h.disks.firstOrNull { it.mount == "/" } ?: h.disks.firstOrNull()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        HostMetric("CPU", h.cpuPercent, h.cpuCores?.let { "$it cores" }, Modifier.weight(1f))
        HostMetric("Memory", h.memPercent, listOfNotNull(h.memUsed, h.memTotal).joinToString(" / ").ifEmpty { null }, Modifier.weight(1f))
        if (disk != null) {
            HostMetric("Disk", disk.percent, listOfNotNull(disk.used, disk.total).joinToString(" / ").ifEmpty { null }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HostMetric(label: String, percent: Double?, sub: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(percent?.let { "%.0f".format(it) } ?: "—", style = MaterialTheme.typography.headlineSmall, fontFamily = GeistMono)
            if (percent != null) {
                Text("%", style = MaterialTheme.typography.bodyMedium, fontFamily = GeistMono, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        HairBar(((percent ?: 0.0) / 100).toFloat())
        sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
