@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.vknxworks.craftyctrl.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.ui.Addon
import com.vknxworks.craftyctrl.ui.AddonsViewModel
import com.vknxworks.craftyctrl.ui.Caption
import com.vknxworks.craftyctrl.ui.ErrorBanner
import com.vknxworks.craftyctrl.ui.LogsViewModel
import com.vknxworks.craftyctrl.ui.theme.ConsoleBackground
import com.vknxworks.craftyctrl.ui.theme.ConsoleDim
import com.vknxworks.craftyctrl.ui.theme.Mono
import androidx.annotation.StringRes
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str
import androidx.compose.ui.res.stringResource

// ================================================================ plugins & mods

@Composable
fun AddonsScreen(api: CraftyApi, serverId: String, onBack: () -> Unit) {
    val vm: AddonsViewModel = viewModel(factory = viewModelFactory { initializer { AddonsViewModel(api, serverId) } })
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val resolver = LocalContext.current.contentResolver
    var uploadTo by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<Addon?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = uploadTo
        if (uri != null && target != null) vm.upload(target, resolver, uri)
        uploadTo = null
    }
    fun pick(folder: String) {
        uploadTo = folder
        com.vknxworks.craftyctrl.data.lock.Lock.expectReturn()
        picker.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*"))
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.page_addons)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.reload)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
        ) {
            if (vm.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            vm.error?.let { item(key = "err") { ErrorBanner(it, onRetry = vm::load) } }
            vm.uploadProgress?.let { p ->
                item(key = "upload") {
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.uploading_x, (p * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item(key = "note") {
                Text(
                    stringResource(R.string.turning_one_off_renames_its_jar),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            if (!vm.loading && vm.error == null && vm.folders.isEmpty()) {
                item(key = "none") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (vm.isBedrock) stringResource(R.string.bedrock_servers_use_add_on_packs)
                            else stringResource(R.string.this_server_has_no_plugins_or),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!vm.isBedrock) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { pick("plugins") }) { Text(stringResource(R.string.add_a_plugin)) }
                                OutlinedButton(onClick = { pick("mods") }) { Text(stringResource(R.string.add_a_mod)) }
                            }
                        }
                    }
                }
            }
            vm.folders.forEach { (folder, list) ->
                item(key = "h:$folder") {
                    Column {
                        Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Caption(stringResource(R.string.x_x_on, folder, list.count { it.enabled }), Modifier.weight(1f))
                            TextButton(onClick = { pick(folder) }, enabled = vm.uploadProgress == null) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (folder == "mods") stringResource(R.string.add_mod) else stringResource(R.string.add_plugin))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                if (list.isEmpty()) {
                    item(key = "e:$folder") {
                        Text(stringResource(R.string.empty), Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(list, key = { it.path }) { a ->
                    Column {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    a.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (a.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (a.enabled) a.fileName else stringResource(R.string.off_x, a.fileName),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = Mono,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { removing = a }, enabled = vm.busy == null) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.remove_x, a.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (vm.busy == a.path) {
                                CircularProgressIndicator(Modifier.padding(horizontal = 14.dp).size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Switch(checked = a.enabled, onCheckedChange = { vm.setEnabled(a, it) }, enabled = vm.busy == null)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    removing?.let { a ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.remove_x_2, a.name)) },
            text = { Text(stringResource(R.string.addon_delete_body, a.fileName, a.folder.substringAfterLast('/'))) },
            confirmButton = { TextButton(onClick = { vm.delete(a); removing = null }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ================================================================ logs

private enum class Level(@StringRes private val labelRes: Int) {
    ALL(R.string.level_all), WARN(R.string.level_warnings), ERROR(R.string.level_errors);

    val label: String get() = str(labelRes)
}

@Composable
fun LogsScreen(api: CraftyApi, serverId: String, onBack: () -> Unit) {
    val vm: LogsViewModel = viewModel(factory = viewModelFactory { initializer { LogsViewModel(api, serverId) } })
    val open = vm.open
    BackHandler(enabled = open != null) { vm.openFile(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(open?.name ?: stringResource(R.string.page_logs), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (open == null) {
                            Text(stringResource(R.string.logs_folder), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (open != null) vm.openFile(null) else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { if (open != null) vm.openFile(open) else vm.loadList() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.reload))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (open == null) LogList(vm) else LogViewer(vm)
        }
    }
}

@Composable
private fun LogList(vm: LogsViewModel) {
    val st = vm.files
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
        if (st.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        st.error?.let { item { ErrorBanner(it, onRetry = vm::loadList) } }
        val list = st.data.orEmpty()
        if (st.data != null && list.isEmpty()) {
            item { Text(stringResource(R.string.no_log_files_yet), Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(list, key = { it.path }) { f ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !f.compressed) { vm.openFile(f) },
            ) {
                Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            f.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = Mono,
                            color = if (f.compressed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            when {
                                f.name == "latest.log" -> stringResource(R.string.current_session)
                                f.compressed -> stringResource(R.string.compressed_download_it_from_the_web)
                                else -> stringResource(R.string.older_log)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!f.compressed) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun LogViewer(vm: LogsViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(Level.ALL) }
    val clipboard = com.vknxworks.craftyctrl.ui.rememberSensitiveClipboard()
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val all = vm.lines.data.orEmpty()
    val shown = remember(all, query, level) {
        all.filter { line ->
            (level == Level.ALL || (level == Level.ERROR && isError(line)) || (level == Level.WARN && (isWarn(line) || isError(line)))) &&
                (query.isBlank() || line.contains(query, ignoreCase = true))
        }
    }
    val listState = rememberLazyListState()
    // Open at the end, where the newest lines are.
    LaunchedEffect(all.size) { if (all.isNotEmpty()) listState.scrollToItem((shown.size - 1).coerceAtLeast(0)) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_this_log)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear)) } },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Level.entries.forEach { l -> FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l.label) }) }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.x_of_x_lines, shown.size, all.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (vm.lines.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            vm.lines.error?.let { ErrorBanner(it, Modifier.padding(16.dp)) }
            if (all.size >= LogsViewModel.MAX_LINES) {
                Text(
                    stringResource(R.string.showing_the_last_x_lines, LogsViewModel.MAX_LINES),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Surface(
                color = ConsoleBackground,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
            ) {
                LazyColumn(state = listState, contentPadding = PaddingValues(12.dp)) {
                    items(shown.size) { i ->
                        val line = shown[i]
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
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        clipboard.copy(line)
                                        scope.launch { snackbar.showSnackbar(str(R.string.line_copied)) }
                                    },
                                ),
                        )
                    }
                    if (shown.isEmpty() && all.isNotEmpty()) {
                        item { Text(stringResource(R.string.no_lines_match), color = ConsoleDim, fontSize = 12.sp) }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
