package com.vknxworks.craftyctrl.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.data.AuditEntry
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.CraftyException
import com.vknxworks.craftyctrl.data.CraftyPermission
import com.vknxworks.craftyctrl.data.PanelRole
import com.vknxworks.craftyctrl.data.PanelUser
import com.vknxworks.craftyctrl.data.RoleServer
import com.vknxworks.craftyctrl.data.ServerPermission
import com.vknxworks.craftyctrl.data.ServerSummary
import com.vknxworks.craftyctrl.data.UserPermissions
import com.vknxworks.craftyctrl.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun Exception.panelMessage(): String {
    if (this is CancellationException) throw this
    // Crafty answers "not allowed" for these with a 400 and NOT_AUTHORIZED.
    if (this is CraftyException && (errorCode == "NOT_AUTHORIZED" || httpCode == 403)) return str(R.string.panel_not_allowed)
    return message?.takeIf { it.isNotBlank() } ?: str(R.string.something_went_wrong)
}

// ---------------------------------------------------------------- audit log

class AuditLogViewModel(private val api: CraftyApi) : ViewModel() {
    var entries by mutableStateOf(Loadable<List<AuditEntry>>(loading = true))
        private set

    /** Server ids to names, so a line can say which server it was about. */
    var serverNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            entries = entries.copy(loading = true, error = null)
            if (serverNames.isEmpty()) {
                serverNames = runCatching { api.servers().associate { it.id to it.name } }.getOrDefault(emptyMap())
            }
            entries = try {
                Loadable(api.auditLog())
            } catch (e: Exception) {
                entries.copy(loading = false, error = e.panelMessage())
            }
        }
    }
}

// ---------------------------------------------------------------- users

/** What the user editor holds while it is open. */
data class UserDraft(
    /** Null for a new user. */
    val id: String?,
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = true,
    val superuser: Boolean = false,
    val roles: Set<String> = emptySet(),
    val permissions: Set<CraftyPermission> = emptySet(),
    /** Per permission, how many servers, users or roles this user may make; -1 = no limit. */
    val limits: Map<CraftyPermission, Int> = CraftyPermission.entries.associateWith { -1 },
    /** False when Crafty wouldn't say which roles and permissions the user has; those are then left alone. */
    val detailsKnown: Boolean = true,
)

class UsersViewModel(private val api: CraftyApi) : ViewModel() {
    var users by mutableStateOf(Loadable<List<PanelUser>>(loading = true))
        private set
    var roles by mutableStateOf<List<PanelRole>>(emptyList())
        private set
    var draft by mutableStateOf<UserDraft?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            users = users.copy(loading = true, error = null)
            users = try {
                Loadable(api.users().sortedBy { it.username.lowercase() })
            } catch (e: Exception) {
                users.copy(loading = false, error = e.panelMessage())
            }
            // The role list needs the "roles" permission; without it the editor just has no roles to tick.
            roles = runCatching { api.roles().sortedBy { it.name.lowercase() } }.getOrDefault(roles)
        }
    }

    fun newUser() {
        draft = UserDraft(id = null)
    }

    /** Opens the editor for [user], with the roles and permissions Crafty has for them. */
    fun edit(user: PanelUser) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                val full = runCatching { api.user(user.id) }.getOrNull()
                val perms: UserPermissions? = runCatching { api.userPermissions(user.id) }.getOrNull()
                draft = UserDraft(
                    id = user.id,
                    username = user.username,
                    enabled = full?.enabled ?: user.enabled,
                    superuser = full?.superuser ?: user.superuser,
                    roles = full?.roles.orEmpty(),
                    permissions = perms?.enabled.orEmpty(),
                    limits = perms?.limits ?: CraftyPermission.entries.associateWith { -1 },
                    detailsKnown = full != null && perms != null,
                )
            } finally {
                busy = false
            }
        }
    }

    fun change(next: UserDraft) {
        draft = next
    }

    fun closeEditor() {
        if (!busy) draft = null
    }

    /** Why the draft can't be saved yet, or null when it can. Crafty's own rules for names and passwords. */
    fun problem(d: UserDraft): String? = when {
        d.id == null && !Regex("^[a-z0-9_]{3,20}$").matches(d.username) -> str(R.string.user_name_rule)
        d.id == null && d.password.length < 8 -> str(R.string.user_password_rule)
        d.id != null && d.password.isNotEmpty() && d.password.length < 8 -> str(R.string.user_password_rule)
        else -> null
    }

    fun save() {
        val d = draft ?: return
        if (busy || problem(d) != null) return
        viewModelScope.launch {
            busy = true
            try {
                val body = buildJsonObject {
                    if (d.id == null) put("username", d.username)
                    if (d.password.isNotEmpty()) put("password", d.password)
                    put("enabled", d.enabled)
                    put("superuser", d.superuser)
                    if (d.detailsKnown) {
                        put("roles", buildJsonArray { d.roles.mapNotNull { it.toIntOrNull() }.forEach { add(JsonPrimitive(it)) } })
                        put(
                            "permissions",
                            buildJsonArray {
                                CraftyPermission.entries.forEach { p ->
                                    add(
                                        buildJsonObject {
                                            put("name", p.name)
                                            put("quantity", d.limits[p] ?: -1)
                                            put("enabled", p in d.permissions)
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
                if (d.id == null) api.createUser(body) else api.updateUser(d.id, body)
                _messages.send(str(if (d.id == null) R.string.user_created_x else R.string.user_saved_x, d.username))
                draft = null
                load()
            } catch (e: Exception) {
                _messages.send(e.panelMessage())
            } finally {
                busy = false
            }
        }
    }

    fun delete(user: PanelUser) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                api.deleteUser(user.id)
                _messages.send(str(R.string.user_deleted_x, user.username))
                draft = null
                load()
            } catch (e: Exception) {
                _messages.send(e.panelMessage())
            } finally {
                busy = false
            }
        }
    }
}

// ---------------------------------------------------------------- roles

/** What the role editor holds while it is open. */
data class RoleDraft(
    /** Null for a new role. */
    val id: String?,
    val name: String = "",
    val mfaRequired: Boolean = false,
    /** Server id to what the role may do there; a server that isn't in the map isn't part of the role. */
    val servers: Map<String, Set<ServerPermission>> = emptyMap(),
    /** False when Crafty wouldn't say which servers the role has (only a superuser may ask); they're then left alone. */
    val serversKnown: Boolean = true,
)

class RolesViewModel(private val api: CraftyApi) : ViewModel() {
    var roles by mutableStateOf(Loadable<List<PanelRole>>(loading = true))
        private set
    var servers by mutableStateOf<List<ServerSummary>>(emptyList())
        private set
    var draft by mutableStateOf<RoleDraft?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            roles = roles.copy(loading = true, error = null)
            roles = try {
                Loadable(api.roles().sortedBy { it.name.lowercase() })
            } catch (e: Exception) {
                roles.copy(loading = false, error = e.panelMessage())
            }
            servers = runCatching { api.servers().sortedBy { it.name.lowercase() } }.getOrDefault(servers)
        }
    }

    fun newRole() {
        draft = RoleDraft(id = null)
    }

    fun edit(role: PanelRole) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                val list: List<RoleServer>? = runCatching { api.roleServers(role.id) }.getOrNull()
                draft = RoleDraft(
                    id = role.id,
                    name = role.name,
                    mfaRequired = role.mfaRequired,
                    servers = list.orEmpty().associate { it.serverId to it.permissions },
                    serversKnown = list != null,
                )
            } finally {
                busy = false
            }
        }
    }

    fun change(next: RoleDraft) {
        draft = next
    }

    fun closeEditor() {
        if (!busy) draft = null
    }

    fun problem(d: RoleDraft): String? = if (d.name.isBlank()) str(R.string.role_name_rule) else null

    fun save() {
        val d = draft ?: return
        if (busy || problem(d) != null) return
        viewModelScope.launch {
            busy = true
            try {
                val body = buildJsonObject {
                    put("name", d.name.trim())
                    put("mfa_required", d.mfaRequired)
                    if (d.serversKnown) {
                        put(
                            "servers",
                            buildJsonArray {
                                d.servers.forEach { (id, perms) ->
                                    add(
                                        buildJsonObject {
                                            put("server_id", id)
                                            put("permissions", RoleServer.maskOf(perms))
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
                if (d.id == null) api.createRole(body) else api.updateRole(d.id, body)
                _messages.send(str(if (d.id == null) R.string.role_created_x else R.string.role_saved_x, d.name.trim()))
                draft = null
                load()
            } catch (e: Exception) {
                _messages.send(e.panelMessage())
            } finally {
                busy = false
            }
        }
    }

    fun delete(role: PanelRole) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                api.deleteRole(role.id)
                _messages.send(str(R.string.role_deleted_x, role.name))
                draft = null
                load()
            } catch (e: Exception) {
                _messages.send(e.panelMessage())
            } finally {
                busy = false
            }
        }
    }
}
