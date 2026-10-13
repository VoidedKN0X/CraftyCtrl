package com.vknxworks.craftyremote.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vknxworks.craftyremote.data.AuthMode
import com.vknxworks.craftyremote.data.BackupConfig
import com.vknxworks.craftyremote.data.CraftyApi
import com.vknxworks.craftyremote.data.CraftyException
import com.vknxworks.craftyremote.data.CredentialStore
import com.vknxworks.craftyremote.data.ScheduledTask
import com.vknxworks.craftyremote.data.ServerStats
import com.vknxworks.craftyremote.data.ServerSummary
import com.vknxworks.craftyremote.data.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private fun Exception.userMessage(): String {
    if (this is CancellationException) throw this
    return message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
}

// ---------------------------------------------------------------- session

class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val store = CredentialStore(app)

    var session by mutableStateOf(store.load())
        private set
    var busy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    val lastUrl: String get() = store.lastUrl
    val lastUsername: String get() = store.lastUsername

    fun signIn(
        url: String,
        mode: AuthMode,
        apiKey: String,
        username: String,
        password: String,
        totp: String,
    ) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            loginError = null
            try {
                val base = CraftyApi.normalizeUrl(url)
                if (base.startsWith("http://", ignoreCase = true)) {
                    loginError = "Use an https:// address — this app only talks to Crafty over HTTPS."
                    return@launch
                }
                val token = when (mode) {
                    AuthMode.API_KEY -> apiKey.trim().removePrefix("Bearer ").trim()
                    AuthMode.PASSWORD -> CraftyApi.login(base, username.trim(), password, totp.trim())
                }
                // Proves the address and token work before saving anything.
                CraftyApi(base, token).servers()
                val s = Session(base, token, mode, username.trim().takeIf { mode == AuthMode.PASSWORD })
                store.save(s)
                session = s
            } catch (e: Exception) {
                loginError = if (e is CraftyException && e.httpCode in listOf(401, 403) && mode == AuthMode.API_KEY) {
                    "Crafty rejected that API key."
                } else {
                    e.userMessage()
                }
            } finally {
                busy = false
            }
        }
    }

    fun signOut(reason: String? = null) {
        store.clear()
        session = null
        loginError = reason
    }
}

// ---------------------------------------------------------------- server list

data class ServerRow(val summary: ServerSummary, val stats: ServerStats?)

class ServerListViewModel(private val api: CraftyApi) : ViewModel() {
    var rows by mutableStateOf<List<ServerRow>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun refresh() {
        try {
            val servers = api.servers()
            val stats = coroutineScope {
                servers.map { s ->
                    async {
                        try {
                            api.stats(s.id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll()
            }
            rows = servers.zip(stats) { summary, st -> ServerRow(summary, st) }
            error = null
        } catch (e: Exception) {
            error = e.userMessage()
        } finally {
            loading = false
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            refreshing = true
            refresh()
            refreshing = false
        }
    }
}

// ---------------------------------------------------------------- server detail

enum class PowerAction(val apiName: String, val label: String, val confirmText: String?, val dangerous: Boolean) {
    START("start_server", "Start", null, false),
    RESTART("restart_server", "Restart", "Players will be disconnected while the server restarts.", false),
    STOP("stop_server", "Stop", "Sends the server's stop command. Players will be disconnected.", false),
    KILL("kill_server", "Kill", "Force-kills the process without saving. Use only if the server is stuck.", true),
}

data class Loadable<T>(val data: T? = null, val loading: Boolean = false, val error: String? = null)

class ServerDetailViewModel(private val api: CraftyApi, val serverId: String) : ViewModel() {
    var stats by mutableStateOf<ServerStats?>(null)
        private set
    var statsError by mutableStateOf<String?>(null)
        private set
    var logs by mutableStateOf<List<String>>(emptyList())
        private set
    var consoleError by mutableStateOf<String?>(null)
        private set
    var backups by mutableStateOf(Loadable<List<BackupConfig>>())
        private set
    var tasks by mutableStateOf(Loadable<List<ScheduledTask>>())
        private set
    var pendingAction by mutableStateOf<PowerAction?>(null)
        private set
    var backupInFlight by mutableStateOf<String?>(null)
        private set
    var sending by mutableStateOf(false)
        private set

    val recentCommands = mutableStateListOf<String>()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    suspend fun refreshStats() {
        try {
            stats = api.stats(serverId)
            statsError = null
        } catch (e: Exception) {
            statsError = e.userMessage()
        }
    }

    suspend fun refreshLogs() {
        try {
            logs = api.logs(serverId)
            consoleError = null
        } catch (e: Exception) {
            consoleError = e.userMessage()
        }
    }

    fun runAction(action: PowerAction) {
        if (pendingAction != null) return
        viewModelScope.launch {
            pendingAction = action
            try {
                api.action(serverId, action.apiName)
                _messages.send("${action.label} sent")
                delay(1500)
                refreshStats()
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                pendingAction = null
            }
        }
    }

    /** [onSent] runs only if Crafty accepted the command, so a failed send keeps the typed text. */
    fun sendCommand(raw: String, onSent: () -> Unit = {}) {
        val command = raw.trim().removePrefix("/").trim()
        if (command.isEmpty() || sending) return
        viewModelScope.launch {
            sending = true
            try {
                api.sendCommand(serverId, command)
                onSent()
                recentCommands.remove(command)
                recentCommands.add(0, command)
                while (recentCommands.size > 8) recentCommands.removeAt(recentCommands.lastIndex)
                delay(600)
                refreshLogs()
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                sending = false
            }
        }
    }

    fun loadBackups() {
        viewModelScope.launch {
            backups = backups.copy(loading = true, error = null)
            backups = try {
                Loadable(api.backups(serverId))
            } catch (e: Exception) {
                backups.copy(loading = false, error = e.userMessage())
            }
        }
    }

    /** [config] null runs the server's default backup. */
    fun runBackup(config: BackupConfig?) {
        if (backupInFlight != null) return
        viewModelScope.launch {
            backupInFlight = config?.id ?: DEFAULT_BACKUP
            try {
                try {
                    api.action(serverId, "backup_server", config?.id)
                } catch (e: CraftyException) {
                    // Older Crafty versions don't take a backup id — fall back to the plain action.
                    if (config?.id == null || e.httpCode !in listOf(400, 404, 405)) throw e
                    api.action(serverId, "backup_server")
                }
                _messages.send("Backup started: ${config?.name ?: "default"}")
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                backupInFlight = null
            }
        }
    }

    fun loadTasks() {
        viewModelScope.launch {
            tasks = tasks.copy(loading = true, error = null)
            tasks = try {
                Loadable(api.tasks(serverId))
            } catch (e: Exception) {
                tasks.copy(loading = false, error = e.userMessage())
            }
        }
    }

    fun setTaskEnabled(task: ScheduledTask, enabled: Boolean) {
        fun swap(value: Boolean) {
            tasks = tasks.copy(data = tasks.data?.map { if (it.id == task.id) it.copy(enabled = value) else it })
        }
        swap(enabled)
        viewModelScope.launch {
            try {
                api.setTaskEnabled(serverId, task.id, enabled)
            } catch (e: Exception) {
                swap(!enabled)
                _messages.send(e.userMessage())
            }
        }
    }

    companion object {
        const val DEFAULT_BACKUP = "__default__"
    }
}
