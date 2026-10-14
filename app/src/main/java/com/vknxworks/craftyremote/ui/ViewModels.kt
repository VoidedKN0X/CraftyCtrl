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
import com.vknxworks.craftyremote.data.FileEntry
import com.vknxworks.craftyremote.data.FileResult
import com.vknxworks.craftyremote.data.HistoryPoint
import com.vknxworks.craftyremote.data.HostStats
import com.vknxworks.craftyremote.data.join
import com.vknxworks.craftyremote.data.ScheduledTask
import com.vknxworks.craftyremote.data.ServerConfig
import com.vknxworks.craftyremote.data.TaskAction
import com.vknxworks.craftyremote.data.ServerStats
import com.vknxworks.craftyremote.data.ServerSummary
import com.vknxworks.craftyremote.data.Session
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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

    var host by mutableStateOf<HostStats?>(null)
        private set
    var hostError by mutableStateOf<String?>(null)
        private set
    /** Host CPU and RAM samples taken while this screen is open, for the sparklines. */
    var hostCpuSamples by mutableStateOf<List<Float>>(emptyList())
        private set
    var hostMemSamples by mutableStateOf<List<Float>>(emptyList())
        private set
    private var hostDenied = false
    private val fallbackVersions = mutableMapOf<String, String?>()

    private suspend fun refreshHost() {
        if (hostDenied) return
        try {
            val h = api.hostStats()
            host = h
            hostError = null
            h.cpuPercent?.let { hostCpuSamples = (hostCpuSamples + it.toFloat()).takeLast(HOST_SAMPLES) }
            h.memPercent?.let { hostMemSamples = (hostMemSamples + it.toFloat()).takeLast(HOST_SAMPLES) }
        } catch (e: CraftyException) {
            hostError = e.message
            // Permission problems won't fix themselves; stop asking every few seconds.
            if (e.httpCode in listOf(400, 403) || e.errorCode == "HOST_STATS") hostDenied = true
        } catch (e: Exception) {
            hostError = e.userMessage()
        }
    }

    /** Refreshes servers and host stats side by side. */
    suspend fun refresh() = coroutineScope {
        launch { refreshHost() }
        refreshServers()
    }

    private suspend fun refreshServers() {
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
            // Fill in versions the stats ping didn't report, once per server.
            for (s in servers) {
                val st = stats[servers.indexOf(s)]
                if (st?.version == null && s.id !in fallbackVersions) {
                    fallbackVersions[s.id] = runCatching { api.versionFallback(s.id, s.executable) }.getOrNull()
                }
            }
            rows = servers.zip(stats) { summary, st ->
                ServerRow(summary, st?.let { it.copy(version = it.version ?: fallbackVersions[summary.id]) })
            }
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

private const val HOST_SAMPLES = 60

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

    /** Raw API responses for troubleshooting; null when the diagnostics view is closed. */
    var diagnostics by mutableStateOf<String?>(null)
        private set

    fun runDiagnostics() {
        diagnostics = "Running…"
        viewModelScope.launch {
            val root = info?.path.orEmpty()
            val checks = listOf(
                Triple("GET", "/servers/$serverId/stats", null),
                Triple("GET", "/servers/$serverId", null),
                Triple("GET", "/servers/$serverId/backups", null),
                Triple("GET", "/servers/$serverId/history", null),
                Triple("GET", "/crafty/stats", null),
                Triple("GET", "/servers/$serverId/backups/backup/${backups.data?.firstOrNull()?.id ?: "default"}/files", null),
                Triple("GET", "/servers/$serverId/backups/backup/${backups.data?.firstOrNull()?.id ?: "default"}", null),
                Triple("POST", "/servers/$serverId/files", """{"page":"files","path":${jsonQuote(root)}}"""),
            )
            val report = StringBuilder("Server $serverId\n")
            for ((method, p, body) in checks) {
                report.append("\n── $method /api/v2$p").append(body?.let { " $it" }.orEmpty()).append('\n')
                report.append(api.raw(p, body)).append('\n')
                diagnostics = report.toString()
            }
        }
    }

    private fun jsonQuote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    // ------------------------------------------------------------ server info

    /** Name, type (Java/Bedrock) and folder; loaded once. */
    var info by mutableStateOf<ServerSummary?>(null)
        private set

    /** Version from metrics or the jar name, used when the stats ping reports none. */
    var fallbackVersion by mutableStateOf<String?>(null)
        private set

    val version: String? get() = stats?.version ?: fallbackVersion

    /** Crafty's own settings for this server; used for the update URL in the menu. */
    var config by mutableStateOf<ServerConfig?>(null)
        private set

    init {
        viewModelScope.launch {
            info = runCatching { api.serverInfo(serverId) }.getOrNull()
            config = runCatching { api.serverConfig(serverId) }.getOrNull()
            fallbackVersion = runCatching { api.versionFallback(serverId, info?.executable) }.getOrNull()
        }
    }

    // ------------------------------------------------------------ stats history

    var history by mutableStateOf(Loadable<List<HistoryPoint>>())
        private set

    /** Samples taken from the live stats poll, used when Crafty has no history to give. */
    var liveSamples by mutableStateOf<List<HistoryPoint>>(emptyList())
        private set

    fun loadHistory() {
        viewModelScope.launch {
            history = history.copy(loading = true, error = null)
            history = try {
                Loadable(api.history(serverId))
            } catch (e: Exception) {
                history.copy(loading = false, error = e.userMessage())
            }
        }
    }

    // ------------------------------------------------------------ files

    var filePath by mutableStateOf<String?>(null)
        private set
    var files by mutableStateOf(Loadable<List<FileEntry>>())
        private set

    /** Opens [path], or the server's root folder when null. */
    fun openFolder(path: String?) {
        viewModelScope.launch {
            val target = path ?: (info ?: runCatching { api.serverInfo(serverId) }.getOrNull().also { info = it })?.path.orEmpty()
            files = files.copy(loading = true, error = null)
            try {
                when (val r = api.browse(serverId, target)) {
                    is FileResult.Folder -> {
                        filePath = target
                        files = Loadable(r.entries)
                    }
                    is FileResult.Text -> {
                        files = files.copy(loading = false, error = "That's a file, not a folder.")
                    }
                }
            } catch (e: Exception) {
                files = files.copy(loading = false, error = e.userMessage())
            }
        }
    }

    val rootPath: String? get() = info?.path

    /** Parent of the current folder, or null at the server's root. */
    val parentPath: String?
        get() {
            val current = filePath?.trimEnd('/') ?: return null
            val root = rootPath?.trimEnd('/') ?: return null
            if (current == root || !current.startsWith("$root/")) return null
            val parent = current.substringBeforeLast('/')
            return if (parent.length < root.length) root else parent
        }

    /** Upload progress 0..1 while a file is uploading, otherwise null. */
    var uploadProgress by mutableStateOf<Float?>(null)
        private set

    fun createEntry(name: String, directory: Boolean) {
        val folder = filePath ?: return
        viewModelScope.launch {
            try {
                api.createEntry(serverId, folder, name, directory)
                _messages.send(if (directory) "Folder created" else "File created")
                openFolder(folder)
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            }
        }
    }

    /** An item picked with Move or Copy, waiting to be pasted into another folder. */
    data class FileClip(val entry: FileEntry, val fromFolder: String, val copy: Boolean)

    var clip by mutableStateOf<FileClip?>(null)
        private set

    fun startClip(entry: FileEntry, copy: Boolean) {
        val folder = filePath ?: return
        clip = FileClip(entry, folder, copy)
    }

    fun cancelClip() {
        clip = null
    }

    private fun insideServer(path: String): Boolean {
        val root = rootPath?.trimEnd('/') ?: return false
        return path.startsWith("$root/") && path.trimEnd('/') != root
    }

    fun renameEntry(entry: FileEntry, newName: String) {
        val folder = filePath ?: return
        if (!insideServer(entry.path)) return
        viewModelScope.launch {
            try {
                api.renameEntry(serverId, entry.path, newName)
                _messages.send("Renamed to $newName")
                openFolder(folder)
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            }
        }
    }

    /** Pastes the clipped item into the folder that's open now. */
    fun pasteHere() {
        val c = clip ?: return
        val dest = filePath ?: return
        val root = rootPath?.trimEnd('/') ?: return
        if (!insideServer(c.entry.path) || !(dest.trimEnd('/') == root || insideServer(dest))) return
        viewModelScope.launch {
            try {
                if (c.copy) {
                    val existing = files.data.orEmpty().map { it.name }.toSet()
                    val target = uniqueName(c.entry.name, existing)
                    val text = when (val r = api.browse(serverId, c.entry.path)) {
                        is FileResult.Text -> r.contents
                        is FileResult.Folder -> throw IllegalStateException("Folders can't be copied.")
                    }
                    api.createEntry(serverId, dest, target, false)
                    api.saveFile(serverId, join(dest, target), text)
                    _messages.send("Copied to $target")
                } else {
                    val destTrim = dest.trimEnd('/')
                    when {
                        destTrim == c.fromFolder.trimEnd('/') -> {
                            _messages.send("It's already in this folder.")
                            return@launch
                        }
                        c.entry.isDir && (destTrim == c.entry.path.trimEnd('/') || destTrim.startsWith(c.entry.path.trimEnd('/') + "/")) -> {
                            _messages.send("A folder can't be moved into itself.")
                            return@launch
                        }
                        files.data.orEmpty().any { it.name == c.entry.name } -> {
                            _messages.send("This folder already has an item called ${c.entry.name}.")
                            return@launch
                        }
                    }
                    api.renameEntry(serverId, c.entry.path, relativePath(c.fromFolder, dest) + c.entry.name)
                    _messages.send("Moved ${c.entry.name}")
                }
                clip = null
                openFolder(dest)
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            }
        }
    }

    fun deleteEntry(entry: FileEntry) {
        val folder = filePath ?: return
        // Never delete the server's own folder or anything outside it.
        if (!insideServer(entry.path)) {
            viewModelScope.launch { _messages.send("That item isn't inside this server's folder.") }
            return
        }
        viewModelScope.launch {
            try {
                api.deleteEntry(serverId, entry.path)
                _messages.send("Deleted ${entry.name}")
                openFolder(folder)
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            }
        }
    }

    /** Uploads a picked file into the open folder. */
    fun upload(resolver: android.content.ContentResolver, uri: android.net.Uri) {
        val folder = filePath ?: return
        if (uploadProgress != null) return
        viewModelScope.launch {
            uploadProgress = 0f
            try {
                var name = "upload.bin"
                var size = -1L
                resolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sz = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (n >= 0) name = c.getString(n) ?: name
                        if (sz >= 0 && !c.isNull(sz)) size = c.getLong(sz)
                    }
                }
                if (size < 0) {
                    _messages.send("Couldn't read the file size.")
                    return@launch
                }
                val input = resolver.openInputStream(uri) ?: run {
                    _messages.send("Couldn't open that file.")
                    return@launch
                }
                input.use { stream ->
                    api.upload(serverId, folder, name, size, { b, off, len -> stream.read(b, off, len) }) { p ->
                        uploadProgress = p
                    }
                }
                _messages.send("Uploaded $name")
                openFolder(folder)
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                uploadProgress = null
            }
        }
    }

    // ------------------------------------------------------------ players

    /** Runs a console command on behalf of a player action and reports the result. */
    fun playerCommand(command: String, done: String) {
        viewModelScope.launch {
            try {
                api.sendCommand(serverId, command)
                _messages.send(done)
                delay(1200)
                refreshStats()
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            }
        }
    }

    fun closeDiagnostics() {
        diagnostics = null
    }

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    suspend fun refreshStats() {
        try {
            val s = api.stats(serverId)
            stats = s
            statsError = null
            liveSamples = (liveSamples + HistoryPoint(
                time = System.currentTimeMillis(),
                cpu = s.cpu,
                memPercent = s.memPercent,
                players = s.online,
            )).takeLast(LIVE_SAMPLES)
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

    var deletingServer by mutableStateOf(false)
        private set

    fun deleteServer(deleteFiles: Boolean, onDeleted: () -> Unit) {
        if (deletingServer) return
        viewModelScope.launch {
            deletingServer = true
            try {
                // Stop it first so Crafty isn't removing a server that still has files open.
                if (stats?.running == true) {
                    runCatching { api.action(serverId, "stop_server") }
                    for (attempt in 0 until 30) { // up to a minute
                        delay(2_000)
                        if (!runCatching { api.stats(serverId).running }.getOrDefault(false)) break
                    }
                }
                api.deleteServer(serverId, deleteFiles)
                onDeleted()
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                deletingServer = false
            }
        }
    }

    /** Set when Start was pressed on a Java server whose EULA isn't accepted yet. */
    var eulaNeeded by mutableStateOf(false)
        private set

    fun dismissEula() {
        eulaNeeded = false
    }

    /** Accepts the EULA (writes eula=true) and then starts the server. */
    fun acceptEulaAndStart() {
        eulaNeeded = false
        val path = info?.path ?: return
        viewModelScope.launch {
            try {
                api.acceptEula(serverId, path)
            } catch (e: Exception) {
                _messages.send("Couldn't write eula.txt: ${e.userMessage()}")
                return@launch
            }
            runAction(PowerAction.START, skipEulaCheck = true)
        }
    }

    fun runAction(action: PowerAction, skipEulaCheck: Boolean = false) {
        if (pendingAction != null) return
        viewModelScope.launch {
            pendingAction = action
            try {
                // A Java server without an accepted EULA silently refuses to start, so ask first.
                val i = info
                if (action == PowerAction.START && !skipEulaCheck && i?.path != null && !i.isBedrock &&
                    api.eulaAccepted(serverId, i.path) == false
                ) {
                    eulaNeeded = true
                    return@launch
                }
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
        // Backup schedules are ordinary scheduled tasks, so the backups tab needs those too.
        if (tasks.data == null && !tasks.loading) loadTasks()
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

    var savingEdit by mutableStateOf(false)
        private set

    /** Creates a task when [existing] is null, otherwise updates it. Calls [onDone] on success. */
    fun saveTask(existing: ScheduledTask?, fields: JsonObject, onDone: () -> Unit) {
        if (savingEdit) return
        viewModelScope.launch {
            savingEdit = true
            try {
                if (existing == null) api.createTask(serverId, fields) else api.updateTask(serverId, existing.id, fields)
                _messages.send(if (existing == null) "Schedule created" else "Schedule saved")
                onDone()
                loadTasks()
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                savingEdit = false
            }
        }
    }

    fun deleteTask(task: ScheduledTask, onDone: () -> Unit) {
        if (savingEdit) return
        viewModelScope.launch {
            savingEdit = true
            try {
                api.deleteTask(serverId, task.id)
                _messages.send("Schedule deleted")
                onDone()
                loadTasks()
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                savingEdit = false
            }
        }
    }

    fun saveBackup(config: BackupConfig, fields: JsonObject, onDone: () -> Unit) {
        if (savingEdit) return
        viewModelScope.launch {
            savingEdit = true
            try {
                api.updateBackup(serverId, config.id, fields)
                _messages.send("Backup settings saved")
                onDone()
                loadBackups()
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                savingEdit = false
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

    // ------------------------------------------------------------ run now, unzip, update, clone, restore

    /** Runs a schedule straight away; older Crafty without a "run" call gets the same action sent directly. */
    fun runTaskNow(task: ScheduledTask) {
        viewModelScope.launch {
            try {
                try {
                    api.runTask(serverId, task.id)
                } catch (e: CraftyException) {
                    if (e.httpCode !in listOf(404, 405)) throw e
                    when (task.kind) {
                        TaskAction.COMMAND -> api.sendCommand(serverId, task.command ?: throw e)
                        TaskAction.BACKUP -> api.action(serverId, "backup_server", task.actionId)
                        TaskAction.START -> api.action(serverId, "start_server")
                        TaskAction.STOP -> api.action(serverId, "stop_server")
                        TaskAction.RESTART -> api.action(serverId, "restart_server")
                        null -> throw e
                    }
                }
                _messages.send("Ran ${task.name}")
                delay(1200)
                refreshStats()
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            }
        }
    }

    fun unzip(entry: FileEntry) {
        val folder = filePath ?: return
        if (!insideServer(entry.path)) return
        viewModelScope.launch {
            try {
                _messages.send("Extracting ${entry.name}…")
                api.unzip(serverId, entry.path)
                _messages.send("Extracted ${entry.name}")
                delay(800)
                openFolder(folder)
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            }
        }
    }

    fun loadConfig() {
        viewModelScope.launch { config = runCatching { api.serverConfig(serverId) }.getOrNull() }
    }

    var managing by mutableStateOf(false)
        private set

    /** Stops the server and waits up to a minute for it to go down. */
    private suspend fun stopAndWait() {
        // A failed status check counts as running, so nothing destructive runs against a live server.
        if (api.stats(serverId).running) {
            api.action(serverId, "stop_server")
            for (attempt in 0 until 30) {
                delay(2_000)
                if (runCatching { !api.stats(serverId).running }.getOrDefault(false)) return
            }
            throw CraftyException(0, "STILL_RUNNING", "The server didn't stop within a minute. Try again, or kill it first.")
        }
    }

    /** Downloads the server jar again from its update URL (Crafty's "Update executable"). */
    fun updateExecutable() {
        if (managing) return
        viewModelScope.launch {
            managing = true
            try {
                stopAndWait()
                api.action(serverId, "update_executable")
                _messages.send("Update started. Crafty downloads the new server jar; start the server when it's done.")
                delay(1500)
                refreshStats()
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                managing = false
            }
        }
    }

    /** Copies the server (files and settings) into a new one, then opens it. */
    fun cloneServer(onCloned: (id: String, name: String) -> Unit) {
        if (managing) return
        viewModelScope.launch {
            managing = true
            try {
                val res = api.actionResult(serverId, "clone_server") as? JsonObject
                val data = res?.get("data") as? JsonObject
                val id = (data?.get("new_server_id") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                    ?: (data?.get("new_server_uuid") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                if (id == null) {
                    _messages.send("Clone started. It appears in the server list when Crafty is done.")
                } else {
                    val name = runCatching { api.serverInfo(id)?.name }.getOrNull() ?: "Copy of ${info?.name.orEmpty()}"
                    onCloned(id, name)
                }
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                managing = false
            }
        }
    }

    /** Archive names per backup profile id (DEFAULT_BACKUP for none); null data means Crafty can't list them. */
    var backupFiles by mutableStateOf(Loadable<List<String>>())
        private set

    fun loadBackupFiles(config: BackupConfig) {
        viewModelScope.launch {
            backupFiles = Loadable(loading = true)
            val location = config.details.firstOrNull { it.first == "Location" }?.second
            backupFiles = try {
                Loadable(api.backupFiles(serverId, config.id, location) ?: emptyList())
            } catch (e: Exception) {
                Loadable(emptyList(), error = e.userMessage())
            }
        }
    }

    fun restoreBackup(config: BackupConfig, fileName: String, inPlace: Boolean, onDone: () -> Unit) {
        if (managing) return
        viewModelScope.launch {
            managing = true
            try {
                stopAndWait()
                api.restoreBackup(serverId, config.id, fileName.trim(), inPlace)
                _messages.send("Restoring $fileName. Start the server when Crafty is done.")
                onDone()
                delay(1500)
                refreshStats()
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                managing = false
            }
        }
    }

    companion object {
        const val DEFAULT_BACKUP = "__default__"
        private const val LIVE_SAMPLES = 200
    }
}

/** "a/b" relative to "a/c/d" -> "../../b/" (always ends with "/" unless empty). */
internal fun relativePath(from: String, to: String): String {
    val f = from.trimEnd('/').split('/').filter { it.isNotEmpty() }
    val t = to.trimEnd('/').split('/').filter { it.isNotEmpty() }
    var common = 0
    while (common < f.size && common < t.size && f[common] == t[common]) common++
    val parts = List(f.size - common) { ".." } + t.drop(common)
    return if (parts.isEmpty()) "" else parts.joinToString("/") + "/"
}

/** "config.yml" -> "config (copy).yml", "config (copy 2).yml", ... when the name is taken. */
internal fun uniqueName(name: String, taken: Set<String>): String {
    if (name !in taken) return name
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val base = name.substring(0, dot)
    val ext = name.substring(dot)
    var n = 1
    while (true) {
        val candidate = if (n == 1) "$base (copy)$ext" else "$base (copy $n)$ext"
        if (candidate !in taken) return candidate
        n++
    }
}
