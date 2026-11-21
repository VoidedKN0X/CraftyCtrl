@file:OptIn(ExperimentalMaterial3Api::class)

package com.vknxworks.craftyctrl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.data.AuditEntry
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.CraftyPermission
import com.vknxworks.craftyctrl.data.PanelRole
import com.vknxworks.craftyctrl.data.PanelUser
import com.vknxworks.craftyctrl.data.ServerPermission
import com.vknxworks.craftyctrl.str
import com.vknxworks.craftyctrl.ui.AuditLogViewModel
import com.vknxworks.craftyctrl.ui.ErrorBanner
import com.vknxworks.craftyctrl.ui.RoleDraft
import com.vknxworks.craftyctrl.ui.RolesViewModel
import com.vknxworks.craftyctrl.ui.SecureScreen
import com.vknxworks.craftyctrl.ui.UserDraft
import com.vknxworks.craftyctrl.ui.UsersViewModel
import com.vknxworks.craftyctrl.ui.components.CtrlBackButton
import com.vknxworks.craftyctrl.ui.components.CtrlButton
import com.vknxworks.craftyctrl.ui.components.CtrlDialog
import com.vknxworks.craftyctrl.ui.components.CtrlIconButton
import com.vknxworks.craftyctrl.ui.components.CtrlListRow
import com.vknxworks.craftyctrl.ui.components.CtrlSheet
import com.vknxworks.craftyctrl.ui.components.CtrlTextField
import com.vknxworks.craftyctrl.ui.components.CtrlTopBar
import com.vknxworks.craftyctrl.ui.lock.findFragmentActivity
import com.vknxworks.craftyctrl.ui.settings.Group
import com.vknxworks.craftyctrl.ui.settings.GroupRow
import com.vknxworks.craftyctrl.ui.settings.GroupSwitch
import com.vknxworks.craftyctrl.ui.theme.CtrlColors
import com.vknxworks.craftyctrl.ui.theme.CtrlType
import com.vknxworks.craftyctrl.ui.verifyUser

// ---------------------------------------------------------------- audit log

/** Crafty's audit log: who did what, on which server and from which address. Read-only, newest first. */
@Composable
fun AuditLogScreen(api: CraftyApi, onBack: () -> Unit) {
    val vm: AuditLogViewModel = viewModel(factory = viewModelFactory { initializer { AuditLogViewModel(api) } })
    var query by rememberSaveable { mutableStateOf("") }
    val state = vm.entries
    val all = state.data.orEmpty()
    val names = vm.serverNames
    val shown = remember(all, query, names) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { e ->
            e.message.contains(q, true) || e.user.contains(q, true) ||
                e.sourceIp?.contains(q, true) == true || names[e.serverId]?.contains(q, true) == true
        }
    }
    Scaffold(
        topBar = {
            CtrlTopBar(
                title = { Text(stringResource(R.string.audit_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { CtrlBackButton(onClick = onBack) },
                actions = { CtrlIconButton(Icons.Filled.Refresh, stringResource(R.string.refresh), onClick = { vm.load() }, enabled = !state.loading) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorBanner(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), onRetry = { vm.load() }) }
            if (all.isNotEmpty()) {
                CtrlTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.audit_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (!state.loading && state.error == null && shown.isEmpty()) {
                Text(
                    stringResource(if (all.isEmpty()) R.string.audit_empty else R.string.audit_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CtrlColors.Muted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                items(shown.size, contentType = { "audit" }) { i -> AuditRow(shown[i], names) }
            }
        }
    }
}

@Composable
private fun AuditRow(e: AuditEntry, serverNames: Map<String, String>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            listOfNotNull(e.time.takeIf { it.isNotBlank() }, e.user.takeIf { it.isNotBlank() }).joinToString("  ·  "),
            style = CtrlType.MonoSmall,
            color = CtrlColors.Muted,
        )
        Text(e.message, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Text)
        val where = listOfNotNull(e.serverId?.let { serverNames[it] ?: it }, e.sourceIp).joinToString("  ·  ")
        if (where.isNotEmpty()) Text(where, style = CtrlType.MonoSmall, color = CtrlColors.Muted)
    }
}

// ---------------------------------------------------------------- users

@Composable
fun UsersScreen(api: CraftyApi, onBack: () -> Unit) {
    val vm: UsersViewModel = viewModel(factory = viewModelFactory { initializer { UsersViewModel(api) } })
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val state = vm.users
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CtrlTopBar(
                title = { Text(stringResource(R.string.users_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { CtrlBackButton(onClick = onBack) },
                actions = { CtrlIconButton(Icons.Filled.Add, stringResource(R.string.user_add), onClick = { vm.newUser() }, enabled = !vm.busy) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading || vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorBanner(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), onRetry = { vm.load() }) }
            val list = state.data.orEmpty()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                items(list, key = { it.id }) { u ->
                    CtrlListRow(
                        title = u.username,
                        subtitle = if (u.superuser) stringResource(R.string.user_superuser) else null,
                        status = stringResource(if (u.enabled) R.string.user_enabled else R.string.user_disabled),
                        statusColor = if (u.enabled) CtrlColors.AccentText else CtrlColors.Inactive,
                        onClick = { vm.edit(u) },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.users_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = CtrlColors.Muted,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
    vm.draft?.let { d ->
        UserEditor(vm, d, state.data.orEmpty().firstOrNull { it.id == d.id })
    }
}

@Composable
private fun UserEditor(vm: UsersViewModel, d: UserDraft, existing: PanelUser?) {
    val activity = LocalContext.current.findFragmentActivity()
    var confirmDelete by remember { mutableStateOf(false) }
    CtrlSheet(onDismissRequest = { vm.closeEditor() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // A password is typed here: no screenshots while the editor is open.
        SecureScreen()
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            Text(
                if (d.id == null) stringResource(R.string.user_add) else d.username,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (d.id == null) {
                    CtrlTextField(
                        value = d.username,
                        onValueChange = { v -> vm.change(d.copy(username = v.lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(20))) },
                        label = { Text(stringResource(R.string.username)) },
                        supportingText = { Text(stringResource(R.string.user_name_rule)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CtrlTextField(
                    value = d.password,
                    onValueChange = { vm.change(d.copy(password = it)) },
                    label = { Text(stringResource(if (d.id == null) R.string.auth_password else R.string.user_new_password)) },
                    supportingText = { Text(stringResource(if (d.id == null) R.string.user_password_rule else R.string.user_new_password_hint)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Group {
                GroupSwitch(stringResource(R.string.user_enabled_switch), stringResource(R.string.user_enabled_hint), d.enabled) { vm.change(d.copy(enabled = it)) }
                GroupSwitch(stringResource(R.string.user_superuser), stringResource(R.string.user_superuser_hint), d.superuser) { vm.change(d.copy(superuser = it)) }
            }
            if (!d.detailsKnown) {
                Text(
                    stringResource(R.string.user_details_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                Group(stringResource(R.string.user_permissions_group), footer = stringResource(R.string.user_limit_hint)) {
                    CraftyPermission.entries.forEach { p ->
                        val on = p in d.permissions
                        GroupSwitch(craftyPermissionLabel(p), null, on) { now ->
                            vm.change(d.copy(permissions = if (now) d.permissions + p else d.permissions - p))
                        }
                        if (on) {
                            val limit = d.limits[p] ?: -1
                            CtrlTextField(
                                value = if (limit < 0) "" else limit.toString(),
                                onValueChange = { v ->
                                    val n = v.filter { it.isDigit() }.take(6).toIntOrNull() ?: -1
                                    vm.change(d.copy(limits = d.limits + (p to n)))
                                },
                                label = { Text(stringResource(R.string.user_limit_label)) },
                                placeholder = { Text(stringResource(R.string.user_limit_none)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                if (vm.roles.isNotEmpty()) {
                    Group(stringResource(R.string.user_roles_group)) {
                        vm.roles.forEach { r ->
                            GroupSwitch(r.name, null, r.id in d.roles) { now ->
                                vm.change(d.copy(roles = if (now) d.roles + r.id else d.roles - r.id))
                            }
                        }
                    }
                }
            }
            val problem = vm.problem(d)
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CtrlButton(
                    onClick = {
                        // Changing who may sign in, and with which rights, asks for your fingerprint or screen lock first.
                        verifyUser(activity, str(R.string.user_verify_title), d.username, onSuccess = { vm.save() })
                    },
                    enabled = problem == null && !vm.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(if (d.id == null) R.string.user_create else R.string.save)) }
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(0.dp))
                        Text(stringResource(R.string.user_delete), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    if (confirmDelete && existing != null) {
        TypedDeleteDialog(
            title = stringResource(R.string.user_delete_title, existing.username),
            body = stringResource(R.string.user_delete_body),
            expected = existing.username,
            busy = vm.busy,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                verifyUser(activity, str(R.string.user_delete_title, existing.username), null, onSuccess = { vm.delete(existing) })
            },
        )
    }
}

@Composable
private fun craftyPermissionLabel(p: CraftyPermission): String = stringResource(
    when (p) {
        CraftyPermission.SERVER_CREATION -> R.string.perm_server_creation
        CraftyPermission.USER_CONFIG -> R.string.perm_user_config
        CraftyPermission.ROLES_CONFIG -> R.string.perm_roles_config
    },
)

/** A delete that only goes ahead after [expected] (a user or role name) was typed, like deleting a server. */
@Composable
private fun TypedDeleteDialog(
    title: String,
    body: String,
    expected: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    CtrlDialog(
        sensitive = true,
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body)
                CtrlTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.type_x_to_confirm, expected)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy && typed.trim() == expected) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------------------------------------------------------------- roles

@Composable
fun RolesScreen(api: CraftyApi, onBack: () -> Unit) {
    val vm: RolesViewModel = viewModel(factory = viewModelFactory { initializer { RolesViewModel(api) } })
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val state = vm.roles
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CtrlTopBar(
                title = { Text(stringResource(R.string.roles_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { CtrlBackButton(onClick = onBack) },
                actions = { CtrlIconButton(Icons.Filled.Add, stringResource(R.string.role_add), onClick = { vm.newRole() }, enabled = !vm.busy) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading || vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorBanner(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), onRetry = { vm.load() }) }
            val list = state.data.orEmpty()
            if (!state.loading && state.error == null && list.isEmpty()) {
                Text(
                    stringResource(R.string.roles_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CtrlColors.Muted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                items(list, key = { it.id }) { r ->
                    CtrlListRow(
                        title = r.name,
                        subtitle = if (r.mfaRequired) stringResource(R.string.role_mfa_required) else null,
                        onClick = { vm.edit(r) },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.roles_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = CtrlColors.Muted,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
    vm.draft?.let { d ->
        RoleEditor(vm, d, state.data.orEmpty().firstOrNull { it.id == d.id })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleEditor(vm: RolesViewModel, d: RoleDraft, existing: PanelRole?) {
    val activity = LocalContext.current.findFragmentActivity()
    var confirmDelete by remember { mutableStateOf(false) }
    CtrlSheet(onDismissRequest = { vm.closeEditor() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            Text(
                if (d.id == null) stringResource(R.string.role_add) else stringResource(R.string.role_edit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            CtrlTextField(
                value = d.name,
                onValueChange = { vm.change(d.copy(name = it.take(60))) },
                label = { Text(stringResource(R.string.role_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Group {
                GroupSwitch(stringResource(R.string.role_mfa_required), stringResource(R.string.role_mfa_hint), d.mfaRequired) {
                    vm.change(d.copy(mfaRequired = it))
                }
            }
            if (!d.serversKnown) {
                Text(
                    stringResource(R.string.role_servers_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else if (vm.servers.isNotEmpty()) {
                Group(stringResource(R.string.role_servers_group), footer = stringResource(R.string.role_servers_hint)) {
                    vm.servers.forEach { s ->
                        val perms = d.servers[s.id]
                        GroupSwitch(s.name, null, perms != null) { now ->
                            vm.change(d.copy(servers = if (now) d.servers + (s.id to emptySet<ServerPermission>()) else d.servers - s.id))
                        }
                        if (perms != null) {
                            FlowRow(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ServerPermission.entries.forEach { p ->
                                    FilterChip(
                                        selected = p in perms,
                                        onClick = {
                                            val next = if (p in perms) perms - p else perms + p
                                            vm.change(d.copy(servers = d.servers + (s.id to next)))
                                        },
                                        label = { Text(serverPermissionLabel(p)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val problem = vm.problem(d)
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CtrlButton(
                    onClick = {
                        // Changing what a role may do on your servers asks for your fingerprint or screen lock first.
                        verifyUser(activity, str(R.string.role_verify_title), d.name, onSuccess = { vm.save() })
                    },
                    enabled = problem == null && !vm.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(if (d.id == null) R.string.role_create else R.string.save)) }
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.role_delete), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    if (confirmDelete && existing != null) {
        TypedDeleteDialog(
            title = stringResource(R.string.role_delete_title, existing.name),
            body = stringResource(R.string.role_delete_body),
            expected = existing.name,
            busy = vm.busy,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                verifyUser(activity, str(R.string.role_delete_title, existing.name), null, onSuccess = { vm.delete(existing) })
            },
        )
    }
}

@Composable
private fun serverPermissionLabel(p: ServerPermission): String = stringResource(
    when (p) {
        ServerPermission.COMMANDS -> R.string.sperm_commands
        ServerPermission.TERMINAL -> R.string.sperm_terminal
        ServerPermission.LOGS -> R.string.sperm_logs
        ServerPermission.SCHEDULE -> R.string.sperm_schedule
        ServerPermission.BACKUP -> R.string.sperm_backup
        ServerPermission.FILES -> R.string.sperm_files
        ServerPermission.CONFIG -> R.string.sperm_config
        ServerPermission.PLAYERS -> R.string.sperm_players
    },
)
