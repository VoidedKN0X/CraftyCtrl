@file:OptIn(ExperimentalMaterial3Api::class)

package com.vknxworks.craftyremote.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.vknxworks.craftyremote.data.CraftyApi
import com.vknxworks.craftyremote.data.G_OTHER
import com.vknxworks.craftyremote.data.PROPERTY_GROUP_ORDER
import com.vknxworks.craftyremote.data.PlayerEntry
import com.vknxworks.craftyremote.data.PlayerList
import com.vknxworks.craftyremote.data.PropDef
import com.vknxworks.craftyremote.data.PropKind
import com.vknxworks.craftyremote.data.ServerConfig
import com.vknxworks.craftyremote.data.memoryOf
import com.vknxworks.craftyremote.data.withMemory
import com.vknxworks.craftyremote.ui.ErrorBanner
import com.vknxworks.craftyremote.ui.OptionsViewModel
import com.vknxworks.craftyremote.ui.PlayersViewModel
import com.vknxworks.craftyremote.ui.PropertiesViewModel
import com.vknxworks.craftyremote.ui.SectionLabel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ================================================================ server.properties

@Composable
fun ServerPropertiesScreen(api: CraftyApi, serverId: String, onBack: () -> Unit) {
    val vm: PropertiesViewModel = viewModel(factory = viewModelFactory { initializer { PropertiesViewModel(api, serverId) } })
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val leave: () -> Unit = { if (vm.dirty) confirmLeave = true else onBack() }
    BackHandler(onBack = leave)

    // Invalid numbers block saving, so a typo can't break the file.
    val invalid = vm.defs.filter { it.kind == PropKind.INT && it.key in vm.edits }
        .filter { intError(it, vm.value(it)) != null }

    fun save() = vm.save { needsRestart ->
        scope.launch {
            if (needsRestart) {
                val r = snackbar.showSnackbar("Saved. Restart the server to apply.", actionLabel = "Restart", duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) vm.restartServer()
            } else {
                snackbar.showSnackbar("Saved. It applies when the server starts.")
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
                        Text("Server settings", fontWeight = FontWeight.Bold)
                        Text("server.properties", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (vm.saving) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 14.dp).size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = vm::load) { Icon(Icons.Filled.Refresh, contentDescription = "Reload") }
                    }
                },
            )
        },
        bottomBar = {
            if (vm.dirty) {
                Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val n = vm.edits.count { (k, v) -> vm.original[k] != v }
                        Text(
                            if (invalid.isNotEmpty()) "Fix ${invalid.size} invalid value${if (invalid.size == 1) "" else "s"}" else "$n unsaved change${if (n == 1) "" else "s"}",
                            modifier = Modifier.weight(1f),
                            color = if (invalid.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(onClick = vm::discard, enabled = !vm.saving) { Text("Discard") }
                        Button(onClick = ::save, enabled = !vm.saving && invalid.isEmpty()) { Text("Save") }
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
                    placeholder = { Text("Search settings") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") } },
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
                            if (vm.isBedrock) "Bedrock server. Settings marked \"not in file\" use the server's default until you change them."
                            else "Settings marked \"not in file\" use the default. Some only exist in older or newer Minecraft versions.",
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
                        item(key = "none") { Text("No settings match \"$query\".", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to server.properties haven't been saved.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep editing") } },
        )
    }
}

private fun intError(def: PropDef, v: String): String? {
    val n = v.trim().toLongOrNull() ?: return "Enter a whole number"
    def.min?.let { if (n < it) return "At least $it" }
    def.max?.let { if (n > it) return "At most $it" }
    return null
}

@Composable
private fun PropertyRow(def: PropDef, value: String, inFile: Boolean, changed: Boolean, onChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (changed) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val caption = buildString {
                append(def.key)
                if (!inFile && !changed) append(" · not in file")
            }
            if (def.kind == PropKind.BOOL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(def.label, style = MaterialTheme.typography.bodyLarge)
                        Text(caption, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = value.trim().equals("true", true), onCheckedChange = { onChange(it.toString()) })
                }
            } else {
                Text(def.label, style = MaterialTheme.typography.bodyLarge)
                Text(caption, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (show) "Hide" else "Show")
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
                title = { Text("Players & bans", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (vm.busy) CircularProgressIndicator(Modifier.padding(horizontal = 14.dp).size(22.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = vm::loadAll) { Icon(Icons.Filled.Refresh, contentDescription = "Reload") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = current },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text(if (current == PlayerList.BANNED_IPS) "Ban an IP" else if (current.name.startsWith("BANNED")) "Ban a player" else "Add") },
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
                            true -> "The server is running, so changes are sent as console commands and apply right away."
                            false -> "The server is stopped, so the app edits the list file directly. It applies on the next start."
                            null -> "Changes apply through the console while the server runs, or to the list file while it's stopped."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (current == PlayerList.WHITELIST) {
                    item(key = "toggle") {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (vm.isBedrock) "Allowlist on" else "Whitelist on", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Only listed players can join.",
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
                        Text("Nobody here yet.", Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(entries.orEmpty(), key = { it.raw.toString() }) { e ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
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
                                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove ${e.label}")
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
                        PlayerList.WHITELIST -> if (vm.isBedrock) "Add to allowlist" else "Add to whitelist"
                        PlayerList.OPS -> "Make operator"
                        PlayerList.BANNED_PLAYERS -> "Ban a player"
                        PlayerList.BANNED_IPS -> "Ban an IP address"
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(if (list == PlayerList.BANNED_IPS) "IP address" else if (vm.isBedrock) "Gamertag" else "Player name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (ban) {
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Reason (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (list == PlayerList.OPS && !vm.isBedrock) {
                        Text("Operators get permission level 4 (full access).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.add(list, name, reason); adding = null },
                    enabled = name.isNotBlank(),
                ) { Text(if (ban) "Ban" else "Add", color = if (ban) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } },
        )
    }

    removing?.let { (list, e) ->
        val verb = when (list) {
            PlayerList.WHITELIST -> "Remove ${e.label} from the ${list.titleFor(vm.isBedrock).lowercase()}?"
            PlayerList.OPS -> "Remove operator rights from ${e.label}?"
            PlayerList.BANNED_PLAYERS, PlayerList.BANNED_IPS -> "Unban ${e.label}?"
        }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(verb) },
            confirmButton = { TextButton(onClick = { vm.remove(list, e); removing = null }) { Text(if (list.name.startsWith("BANNED")) "Unban" else "Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
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
                        Text("Server options", fontWeight = FontWeight.Bold)
                        Text("Crafty's settings for this server", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Filled.Refresh, contentDescription = "Reload") } },
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
    var commandEdited by remember { mutableStateOf(false) }

    val hasMemory = !bedrock && (minMem0 != null || maxMem0 != null || c.executionCommand?.contains("java") == true)
    val minN = minMem.toIntOrNull()
    val maxN = maxMem.toIntOrNull()
    val memChanged = minN != minMem0 || maxN != maxMem0
    val memError = when {
        !hasMemory || !memChanged -> null
        minN == null || maxN == null -> "Enter both values in MB"
        minN < 128 -> "Minimum must be at least 128 MB"
        maxN < minN -> "Maximum must be at least the minimum"
        else -> null
    }
    val portError = when {
        port.isBlank() -> null
        port.toIntOrNull() == null -> "Enter a number"
        port.toInt() !in 1..65535 -> "1–65535"
        else -> null
    }

    // The start command, with the memory fields applied unless it was edited by hand.
    val finalCommand = when {
        commandEdited -> command
        hasMemory && memChanged && memError == null -> withMemory(command, minN!!, maxN!!)
        else -> command
    }

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
        if (ip != c.serverIp.orEmpty()) m["server_ip"] = JsonPrimitive(ip.trim())
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
        SectionLabel("General")
        OutlinedTextField(name, { name = it }, label = { Text("Server name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SwitchRow("Start with Crafty", "Starts this server when Crafty starts.", autoStart) { autoStart = it }
        if (autoStart) NumberField("Start delay (seconds)", autoDelay) { autoDelay = it }
        SwitchRow("Crash detection", "Crafty restarts the server if it crashes.", crash) { crash = it }

        if (hasMemory) {
            SectionLabel("Memory")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Min (MB)", minMem, Modifier.weight(1f)) { minMem = it; commandEdited = false; command = c.executionCommand.orEmpty() }
                NumberField("Max (MB)", maxMem, Modifier.weight(1f)) { maxMem = it; commandEdited = false; command = c.executionCommand.orEmpty() }
            }
            Text(
                memError ?: "Sets -Xms and -Xmx in the start command. Restart the server to apply.",
                style = MaterialTheme.typography.bodySmall,
                color = if (memError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel("Stopping")
        OutlinedTextField(stopCmd, { stopCmd = it }, label = { Text("Stop command") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        NumberField("Shutdown timeout (seconds)", timeout) { timeout = it }

        SectionLabel("Network")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(ip, { ip = it }, label = { Text("Address Crafty checks") }, singleLine = true, modifier = Modifier.weight(1.4f))
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit) },
                label = { Text("Port") }, singleLine = true, isError = portError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Where Crafty pings the server for players and version. It doesn't change the game port; set that in Server settings too.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (c.showStatus != null) SwitchRow("Show on status page", "List this server on Crafty's public status page.", showStatus) { showStatus = it }
        if (c.countPlayers != null) SwitchRow("Count players", "Include its players in Crafty's totals.", countPlayers) { countPlayers = it }

        SectionLabel("Maintenance")
        NumberField("Delete logs after (days)", logsDays) { logsDays = it }
        OutlinedTextField(
            updateUrl, { updateUrl = it },
            label = { Text("Server jar update URL") },
            supportingText = { Text("Used by Update server software. Point it at the new jar's download link.") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel("Start command")
        OutlinedTextField(
            value = finalCommand,
            onValueChange = { command = it; commandEdited = true },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            supportingText = { Text("Advanced. Crafty may only let admins change this.") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = { onSave(fields) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (fields.isEmpty()) "No changes" else "Save ${fields.size} change${if (fields.size == 1) "" else "s"}")
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
