@file:OptIn(ExperimentalMaterial3Api::class)

package com.vknxworks.craftyctrl.ui.screens

import com.vknxworks.craftyctrl.ui.theme.Mono
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.G_OTHER
import com.vknxworks.craftyctrl.data.PROPERTY_GROUP_ORDER
import com.vknxworks.craftyctrl.data.PlayerEntry
import com.vknxworks.craftyctrl.data.PlayerList
import com.vknxworks.craftyctrl.data.PropDef
import com.vknxworks.craftyctrl.data.PropKind
import com.vknxworks.craftyctrl.data.ServerConfig
import com.vknxworks.craftyctrl.data.canSetMemory
import com.vknxworks.craftyctrl.data.memoryOf
import com.vknxworks.craftyctrl.data.withMemory
import com.vknxworks.craftyctrl.ui.ErrorBanner
import com.vknxworks.craftyctrl.ui.OptionsViewModel
import com.vknxworks.craftyctrl.ui.PlayersViewModel
import com.vknxworks.craftyctrl.ui.PropertiesViewModel
import com.vknxworks.craftyctrl.ui.SectionLabel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.vknxworks.craftyctrl.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vknxworks.craftyctrl.str

// ================================================================ server.properties

@Composable
fun ServerPropertiesScreen(api: CraftyApi, serverId: String, onBack: () -> Unit) {
    // RCON and management secrets can be revealed here, so screenshots stay blocked like in the file editor.
    com.vknxworks.craftyctrl.ui.SecureScreen()
    val vm: PropertiesViewModel = viewModel(factory = viewModelFactory { initializer { PropertiesViewModel(api, serverId) } })
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val leave: () -> Unit = { if (vm.dirty) confirmLeave = true else onBack() }
    BackHandler(onBack = leave)

    // Invalid numbers block saving, so a typo can't break the file.
    val invalid = vm.defs.filter { it.kind == PropKind.INT && it.key in vm.edits && vm.edits[it.key] != vm.original[it.key] }
        .filter { intError(it, vm.value(it)) != null }

    fun save() = vm.save { needsRestart ->
        scope.launch {
            if (needsRestart) {
                val r = snackbar.showSnackbar(str(R.string.saved_restart_the_server_to_apply), actionLabel = str(R.string.power_restart), duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) vm.restartServer()
            } else {
                snackbar.showSnackbar(str(R.string.saved_it_applies_when_the_server))
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.page_properties), fontWeight = FontWeight.Bold)
                        Text("server.properties", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                actions = {
                    if (vm.saving) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 14.dp).size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = vm::load) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.reload)) }
                    }
                },
            )
        },
        bottomBar = {
            if (vm.dirty) {
                Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val n = vm.edits.count { (k, v) -> vm.original[k] != v }
                        Text(
                            if (invalid.isNotEmpty()) pluralStringResource(R.plurals.fix_invalid_values, invalid.size, invalid.size) else pluralStringResource(R.plurals.unsaved_changes, n, n),
                            modifier = Modifier.weight(1f),
                            color = if (invalid.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(onClick = vm::discard, enabled = !vm.saving) { Text(stringResource(R.string.discard)) }
                        Button(onClick = ::save, enabled = !vm.saving && invalid.isEmpty()) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.error?.let { ErrorBanner(it, Modifier.padding(16.dp), onRetry = vm::load) }
            if (vm.error == null && !vm.loading || vm.original.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_settings)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear)) } },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                val q = query.trim().lowercase()
                fun matches(key: String, label: String) = q.isEmpty() || q in key.lowercase() || q in label.lowercase()
                val known = vm.defs.filter { matches(it.key, it.label) }
                val other = vm.unknownKeys.filter { matches(it, it) }
                    .map { PropDef(it, it, G_OTHER, PropKind.TEXT, "") }
                val grouped = (known + other).groupBy { it.group }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "note") {
                        Text(
                            if (vm.isBedrock) stringResource(R.string.bedrock_server_settings_marked_not_in)
                            else stringResource(R.string.settings_marked_not_in_file_use),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PROPERTY_GROUP_ORDER.forEach { group ->
                        val defs = grouped[group].orEmpty()
                        if (defs.isEmpty()) return@forEach
                        item(key = "g:$group") { SectionLabel(group, Modifier.padding(top = 10.dp)) }
                        items(defs, key = { "p:" + it.key }) { def ->
                            PropertyRow(
                                def = def,
                                value = vm.value(def),
                                inFile = vm.isSet(def.key),
                                changed = def.key in vm.edits && vm.edits[def.key] != vm.original[def.key],
                                onChange = { vm.set(def.key, it) },
                            )
                        }
                    }
                    if (grouped.isEmpty() && !vm.loading) {
                        item(key = "none") { Text(stringResource(R.string.no_settings_match_x, query), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.discard_changes)) },
            text = { Text(stringResource(R.string.your_changes_to_server_properties_haven)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.keep_editing)) } },
        )
    }
}

private fun intError(def: PropDef, v: String): String? {
    val n = v.trim().toLongOrNull() ?: return str(R.string.enter_a_whole_number)
    def.min?.let { if (n < it) return str(R.string.at_least_x, it) }
    def.max?.let { if (n > it) return str(R.string.at_most_x, it) }
    return null
}

@Composable
private fun PropertyRow(def: PropDef, value: String, inFile: Boolean, changed: Boolean, onChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (changed) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val caption = if (!inFile && !changed) stringResource(R.string.x_not_in_file, def.key) else def.key
            if (def.kind == PropKind.BOOL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(def.label, style = MaterialTheme.typography.bodyLarge)
                        Text(caption, style = MaterialTheme.typography.labelSmall, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = value.trim().equals("true", true), onCheckedChange = { onChange(it.toString()) })
                }
            } else {
                Text(def.label, style = MaterialTheme.typography.bodyLarge)
                Text(caption, style = MaterialTheme.typography.labelSmall, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (def.kind) {
                    PropKind.CHOICE -> ChoiceField(def, value, onChange)
                    PropKind.INT -> {
                        val err = intError(def, value)
                        OutlinedTextField(
                            value = value,
                            onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '-' }) },
                            singleLine = true,
                            isError = err != null,
                            supportingText = err?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    PropKind.SECRET -> {
                        var show by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = value,
                            onValueChange = onChange,
                            singleLine = true,
                            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { show = !show }) {
                                    Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (show) stringResource(R.string.hide) else stringResource(R.string.show))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> OutlinedTextField(
                        value = value,
                        onValueChange = onChange,
                        singleLine = def.key != "motd" && def.key != "generator-settings",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            def.help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ChoiceField(def: PropDef, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // Keep whatever the file has, even if it's not one of the usual choices.
    val choices = (def.choices + value).filter { it.isNotBlank() }.distinct()
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { c ->
                DropdownMenuItem(text = { Text(c) }, onClick = { open = false; onChange(c) })
            }
        }
    }
}

// ================================================================ players & bans

@Composable
fun PlayersScreen(api: CraftyApi, serverId: String, onBack: () -> Unit) {
    val vm: PlayersViewModel = viewModel(factory = viewModelFactory { initializer { PlayersViewModel(api, serverId) } })
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf<PlayerList?>(null) }
    var removing by remember { mutableStateOf<Pair<PlayerList, PlayerEntry>?>(null) }
    val lists = PlayerList.forServer(vm.isBedrock)
    val current = lists.getOrElse(tab) { lists.first() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.page_players), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                actions = {
                    if (vm.busy) CircularProgressIndicator(Modifier.padding(horizontal = 14.dp).size(22.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = vm::loadAll) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.reload)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = current },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text(if (current == PlayerList.BANNED_IPS) stringResource(R.string.ban_an_ip) else if (current.name.startsWith("BANNED")) stringResource(R.string.ban_a_player) else stringResource(R.string.add)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (lists.size > 1) {
                ScrollableTabRow(selectedTabIndex = lists.indexOf(current), edgePadding = 16.dp) {
                    lists.forEachIndexed { i, l ->
                        Tab(selected = l == current, onClick = { tab = i }, text = { Text(l.titleFor(vm.isBedrock)) })
                    }
                }
            }
            vm.loadError?.let { ErrorBanner(it, Modifier.padding(16.dp), onRetry = vm::loadAll) }
            val state = vm.lists[current]
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "how") {
                    Text(
                        when (vm.running) {
                            true -> stringResource(R.string.the_server_is_running_so_changes)
                            false -> stringResource(R.string.the_server_is_stopped_so_the)
                            null -> stringResource(R.string.changes_apply_through_the_console_while)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (current == PlayerList.WHITELIST) {
                    item(key = "toggle") {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (vm.isBedrock) stringResource(R.string.allowlist_on) else stringResource(R.string.whitelist_on), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        stringResource(R.string.only_listed_players_can_join),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = vm.whitelistOn == true, enabled = vm.whitelistOn != null && !vm.busy, onCheckedChange = vm::setWhitelist)
                            }
                        }
                    }
                }
                if (state?.loading == true && state.data == null) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state?.error?.let { item(key = "err") { ErrorBanner(it) } }
                val entries = state?.data
                if (entries != null && entries.isEmpty()) {
                    item(key = "empty") {
                        Text(stringResource(R.string.nobody_here_yet), Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                itemsIndexed(entries.orEmpty(), key = { i, e -> "$i:${e.raw}" }) { _, e ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (current == PlayerList.BANNED_IPS) Icons.Filled.Public else if (current == PlayerList.OPS) Icons.Filled.Shield else Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                listOfNotNull(e.detail, e.id?.takeIf { e.name != null || e.ip != null }).forEach {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            IconButton(onClick = { removing = current to e }, enabled = !vm.busy) {
                                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = stringResource(R.string.remove_x, e.label))
                            }
                        }
                    }
                }
            }
        }
    }

    adding?.let { list ->
        var name by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        val ban = list == PlayerList.BANNED_PLAYERS || list == PlayerList.BANNED_IPS
        AlertDialog(
            onDismissRequest = { adding = null },
            title = {
                Text(
                    when (list) {
                        PlayerList.WHITELIST -> if (vm.isBedrock) stringResource(R.string.add_to_allowlist) else stringResource(R.string.add_to_whitelist)
                        PlayerList.OPS -> stringResource(R.string.make_operator)
                        PlayerList.BANNED_PLAYERS -> stringResource(R.string.ban_a_player)
                        PlayerList.BANNED_IPS -> stringResource(R.string.ban_an_ip_address)
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(if (list == PlayerList.BANNED_IPS) stringResource(R.string.ip_address) else if (vm.isBedrock) stringResource(R.string.gamertag) else stringResource(R.string.player_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (ban) {
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text(stringResource(R.string.reason_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (list == PlayerList.OPS && !vm.isBedrock) {
                        Text(stringResource(R.string.operators_get_permission_level_4_full), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.add(list, name, reason); adding = null },
                    enabled = name.isNotBlank(),
                ) { Text(if (ban) stringResource(R.string.ban) else stringResource(R.string.add), color = if (ban) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    removing?.let { (list, e) ->
        val verb = when (list) {
            PlayerList.WHITELIST -> stringResource(R.string.remove_x_from_the_x, e.label, list.titleFor(vm.isBedrock).lowercase())
            PlayerList.OPS -> stringResource(R.string.remove_operator_rights_from_x, e.label)
            PlayerList.BANNED_PLAYERS, PlayerList.BANNED_IPS -> stringResource(R.string.unban_x, e.label)
        }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(verb) },
            confirmButton = { TextButton(onClick = { vm.remove(list, e); removing = null }) { Text(if (list.name.startsWith("BANNED")) stringResource(R.string.unban) else stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ================================================================ Crafty server options

@Composable
fun ServerOptionsScreen(api: CraftyApi, serverId: String, onBack: () -> Unit) {
    val vm: OptionsViewModel = viewModel(factory = viewModelFactory { initializer { OptionsViewModel(api, serverId) } })
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.page_options), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.crafty_s_settings_for_this_server), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.reload)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val st = vm.config
            if (st.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            st.error?.let { ErrorBanner(it, Modifier.padding(16.dp), onRetry = vm::load) }
            // Re-key on the loaded config so the form resets after a save or reload.
            st.data?.let { c -> key(c) { OptionsForm(c, vm.isBedrock, vm.saving) { fields -> vm.save(fields) {} } } }
        }
    }
}

@Composable
private fun OptionsForm(c: ServerConfig, bedrock: Boolean, saving: Boolean, onSave: (JsonObject) -> Unit) {
    val (minMem0, maxMem0) = memoryOf(c.executionCommand)
    var name by remember { mutableStateOf(c.name) }
    var autoStart by remember { mutableStateOf(c.autoStart) }
    var autoDelay by remember { mutableStateOf(c.autoStartDelay?.toString().orEmpty()) }
    var crash by remember { mutableStateOf(c.crashDetection) }
    var minMem by remember { mutableStateOf(minMem0?.toString().orEmpty()) }
    var maxMem by remember { mutableStateOf(maxMem0?.toString().orEmpty()) }
    var stopCmd by remember { mutableStateOf(c.stopCommand.orEmpty()) }
    var timeout by remember { mutableStateOf(c.shutdownTimeout?.toString().orEmpty()) }
    var logsDays by remember { mutableStateOf(c.logsDeleteAfter?.toString().orEmpty()) }
    var port by remember { mutableStateOf(c.serverPort?.toString().orEmpty()) }
    var ip by remember { mutableStateOf(c.serverIp.orEmpty()) }
    var updateUrl by remember { mutableStateOf(c.updateUrl.orEmpty()) }
    var showStatus by remember { mutableStateOf(c.showStatus ?: true) }
    var countPlayers by remember { mutableStateOf(c.countPlayers ?: true) }
    var command by remember { mutableStateOf(c.executionCommand.orEmpty()) }

    val hasMemory = !bedrock && canSetMemory(c.executionCommand)
    val minN = minMem.toIntOrNull()
    val maxN = maxMem.toIntOrNull()
    // Compared with what the command (possibly hand-edited) currently says.
    val memChanged = (minN to maxN) != memoryOf(command)
    val memError = when {
        !hasMemory || !memChanged -> null
        minN == null || maxN == null -> stringResource(R.string.enter_both_values_in_mb)
        minN < 128 -> stringResource(R.string.minimum_must_be_at_least_128)
        maxN < minN -> stringResource(R.string.maximum_must_be_at_least_the)
        else -> null
    }
    val portError = when {
        port.isBlank() -> null
        port.toIntOrNull() == null -> stringResource(R.string.enter_a_number)
        port.toInt() !in 1..65535 -> "1–65535"
        else -> null
    }

    // The start command with the memory fields applied.
    val finalCommand = if (hasMemory && memChanged && memError == null) withMemory(command, minN!!, maxN!!) else command

    val fields: JsonObject = run {
        val m = linkedMapOf<String, JsonPrimitive>()
        if (name.trim() != c.name && name.isNotBlank()) m["server_name"] = JsonPrimitive(name.trim())
        if (autoStart != c.autoStart) m["auto_start"] = JsonPrimitive(autoStart)
        autoDelay.toIntOrNull()?.let { if (it != c.autoStartDelay) m["auto_start_delay"] = JsonPrimitive(it) }
        if (crash != c.crashDetection) m["crash_detection"] = JsonPrimitive(crash)
        if (finalCommand != c.executionCommand.orEmpty() && finalCommand.isNotBlank()) m["execution_command"] = JsonPrimitive(finalCommand)
        if (stopCmd != c.stopCommand.orEmpty() && stopCmd.isNotBlank()) m["stop_command"] = JsonPrimitive(stopCmd)
        timeout.toIntOrNull()?.let { if (it != c.shutdownTimeout) m["shutdown_timeout"] = JsonPrimitive(it) }
        logsDays.toIntOrNull()?.let { if (it != c.logsDeleteAfter) m["logs_delete_after"] = JsonPrimitive(it) }
        port.toIntOrNull()?.let { if (it != c.serverPort && portError == null) m["server_port"] = JsonPrimitive(it) }
        if (ip.isNotBlank() && ip.trim() != c.serverIp.orEmpty()) m["server_ip"] = JsonPrimitive(ip.trim())
        if (updateUrl.trim() != c.updateUrl.orEmpty()) m["executable_update_url"] = JsonPrimitive(updateUrl.trim())
        if (c.showStatus != null && showStatus != c.showStatus) m["show_status"] = JsonPrimitive(showStatus)
        if (c.countPlayers != null && countPlayers != c.countPlayers) m["count_players"] = JsonPrimitive(countPlayers)
        JsonObject(m)
    }
    val canSave = fields.isNotEmpty() && memError == null && portError == null && !saving

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(stringResource(R.string.general))
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.server_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SwitchRow(stringResource(R.string.start_with_crafty), stringResource(R.string.starts_this_server_when_crafty_starts), autoStart) { autoStart = it }
        if (autoStart) NumberField(stringResource(R.string.start_delay_seconds), autoDelay) { autoDelay = it }
        SwitchRow(stringResource(R.string.crash_detection), stringResource(R.string.crafty_restarts_the_server_if_it), crash) { crash = it }

        if (hasMemory) {
            SectionLabel(stringResource(R.string.memory))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(stringResource(R.string.min_mb), minMem, Modifier.weight(1f)) { minMem = it }
                NumberField(stringResource(R.string.max_mb), maxMem, Modifier.weight(1f)) { maxMem = it }
            }
            Text(
                memError ?: stringResource(R.string.sets_xms_and_xmx_in_the),
                style = MaterialTheme.typography.bodySmall,
                color = if (memError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel(stringResource(R.string.stopping))
        OutlinedTextField(stopCmd, { stopCmd = it }, label = { Text(stringResource(R.string.stop_command)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        NumberField(stringResource(R.string.shutdown_timeout_seconds), timeout) { timeout = it }

        SectionLabel(stringResource(R.string.group_network))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(ip, { ip = it }, label = { Text(stringResource(R.string.address_crafty_checks)) }, singleLine = true, modifier = Modifier.weight(1.4f))
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.port)) }, singleLine = true, isError = portError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.where_crafty_pings_the_server_for),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (c.showStatus != null) SwitchRow(stringResource(R.string.show_on_status_page), stringResource(R.string.list_this_server_on_crafty_s), showStatus) { showStatus = it }
        if (c.countPlayers != null) SwitchRow(stringResource(R.string.count_players), stringResource(R.string.include_its_players_in_crafty_s), countPlayers) { countPlayers = it }

        SectionLabel(stringResource(R.string.maintenance))
        NumberField(stringResource(R.string.delete_logs_after_days), logsDays) { logsDays = it }
        OutlinedTextField(
            updateUrl, { updateUrl = it },
            label = { Text(stringResource(R.string.server_jar_update_url)) },
            supportingText = { Text(stringResource(R.string.used_by_update_server_software_point)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel(stringResource(R.string.start_command))
        OutlinedTextField(
            value = finalCommand,
            onValueChange = { edited ->
                // A hand edit wins; the memory fields follow whatever it now says.
                command = edited
                memoryOf(edited).let { (lo, hi) -> minMem = lo?.toString().orEmpty(); maxMem = hi?.toString().orEmpty() }
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
            supportingText = { Text(stringResource(R.string.advanced_crafty_may_only_let_admins)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = { onSave(fields) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (fields.isEmpty()) stringResource(R.string.no_changes) else pluralStringResource(R.plurals.save_changes, fields.size, fields.size))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
