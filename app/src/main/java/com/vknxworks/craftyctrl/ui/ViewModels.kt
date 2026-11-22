package com.vknxworks.craftyctrl.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vknxworks.craftyctrl.data.AuthMode
import com.vknxworks.craftyctrl.data.BackupConfig
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.CraftyException
import com.vknxworks.craftyctrl.data.CredentialStore
import com.vknxworks.craftyctrl.data.FileEntry
import com.vknxworks.craftyctrl.data.FileResult
import com.vknxworks.craftyctrl.data.HistoryPoint
import com.vknxworks.craftyctrl.data.HostStats
import com.vknxworks.craftyctrl.data.join
import com.vknxworks.craftyctrl.data.ScheduledTask
import com.vknxworks.craftyctrl.data.ServerConfig
import com.vknxworks.craftyctrl.data.TaskAction
import com.vknxworks.craftyctrl.data.ServerStats
import com.vknxworks.craftyctrl.data.ServerSummary
import com.vknxworks.craftyctrl.data.Session
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
import androidx.annotation.StringRes
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.vknxworks.craftyctrl.plural

private fun Exception.userMessage(): String {
    if (this is CancellationException) throw this
    return message?.takeIf { it.isNotBlank() } ?: str(R.string.something_went_wrong)
}

// ---------------------------------------------------------------- session

class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val store = CredentialStore(app)
    private val settings = com.vknxworks.craftyctrl.data.AppSettings(app)

    var session by mutableStateOf(store.load())
        private set

    /** Bumped on every sign-in, so a new sign-in gets fresh screens. */
    var signIns by mutableStateOf(0)
        private set

    init {
        // Null while the app lock keeps the token sealed; the lock fills it in when you unlock. Read off the
        // main thread (it asks the Keystore); whatever needs the token before then waits for it.
        com.vknxworks.craftyctrl.data.lock.Lock.readTokenAtStart { store.token() }
    }
    var busy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    val lastUrl: String get() = store.lastUrl
    val lastUsername: String get() = store.lastUsername

    /** The other panels CraftyCtrl is signed in to (the one on screen is [session]). */
    var otherPanels by mutableStateOf(store.others())
        private set

    /** True while the sign-in form is open to add another panel next to the one that's signed in. */
    var addingPanel by mutableStateOf(false)
        private set

    /** The panel's certificate isn't trusted by the phone: shown so the user can confirm it by its fingerprint. */
    var certPrompt by mutableStateOf<com.vknxworks.craftyctrl.data.CertInfo?>(null)
        private set

    fun dismissCertPrompt() {
        certPrompt = null
    }

    /** The fingerprint on screen was confirmed: from now on exactly this certificate is accepted for this panel. */
    fun trustCertificate(info: com.vknxworks.craftyctrl.data.CertInfo) {
        com.vknxworks.craftyctrl.data.CertPins.accept(info.baseUrl, info.fingerprint)
        certPrompt = null
    }

    fun startAddPanel() {
        loginError = null
        addingPanel = true
    }

    fun cancelAddPanel() {
        loginError = null
        certPrompt = null
        addingPanel = false
    }

    /** Switches to another signed-in panel. Its token is read off the main thread (it asks the Keystore). */
    fun switchTo(panel: com.vknxworks.craftyctrl.data.SavedPanel) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                val token = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (!store.activate(panel)) return@withContext null
                    store.syncBackgroundCopy(settings.alertsEnabled)
                    store.token()
                }
                otherPanels = store.others()
                if (token == null) {
                    // The saved login can't be read any more: this panel needs a new sign-in.
                    store.clear()
                    com.vknxworks.craftyctrl.data.lock.Lock.liveToken = null
                    session = null
                    loginError = str(R.string.panel_login_unreadable)
                } else {
                    com.vknxworks.craftyctrl.data.lock.Lock.liveToken = token
                    loginError = null
                    addingPanel = false
                    // What the alert checks remembered belongs to the panel that was on screen before.
                    com.vknxworks.craftyctrl.monitor.Checker.reset(getApplication<Application>())
                    signIns++
                    session = store.load()
                }
            } finally {
                busy = false
            }
        }
    }

    /** Forgets one of the other panels on this phone. */
    fun removePanel(panel: com.vknxworks.craftyctrl.data.SavedPanel) {
        store.removeOther(panel)
        otherPanels = store.others()
        if (!store.usesAddress(panel.baseUrl)) com.vknxworks.craftyctrl.data.CertPins.forget(panel.baseUrl)
    }

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
                // Plain HTTP only for a panel the user accepted in the "unencrypted connection" warning.
                if (com.vknxworks.craftyctrl.data.CleartextPolicy.isHttp(base) &&
                    !com.vknxworks.craftyctrl.data.CleartextPolicy.isAccepted(base)
                ) {
                    loginError = str(R.string.err_cleartext_blocked, base.toHttpUrlOrNull()?.host ?: base)
                    return@launch
                }
                val token = when (mode) {
                    AuthMode.API_KEY -> apiKey.trim().removePrefix("Bearer ").trim()
                    AuthMode.PASSWORD -> CraftyApi.login(base, username.trim(), password, totp.trim())
                }
                // Proves the address and token work before saving anything.
                CraftyApi(base, token).servers()
                val s = Session(base, mode, username.trim().takeIf { mode == AuthMode.PASSWORD })
                // Adding a panel: the one that was signed in stays signed in and waits with the others.
                if (session != null && session != s) store.stashActive()
                store.save(s, token)
                store.syncBackgroundCopy(settings.alertsEnabled)
                com.vknxworks.craftyctrl.data.lock.Lock.liveToken = token
                otherPanels = store.others()
                addingPanel = false
                signIns++
                session = s
            } catch (e: Exception) {
                loginError = if (e is CraftyException && e.httpCode in listOf(401, 403) && mode == AuthMode.API_KEY) {
                    str(R.string.crafty_rejected_that_api_key)
                } else {
                    e.userMessage()
                }
                // A certificate the phone doesn't trust (Crafty's own self-signed one, or one that changed):
                // read it without sending anything and let the user confirm it by its fingerprint.
                if (e is CraftyException && e.errorCode in listOf("CERT", "CERT_CHANGED")) {
                    val base = runCatching { CraftyApi.normalizeUrl(url) }.getOrNull()
                    if (base != null) {
                        certPrompt = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { com.vknxworks.craftyctrl.data.CertPins.inspect(base) }.getOrNull()
                        }
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    fun signOut(reason: String? = null) {
        store.clear()
        otherPanels = store.others()
        addingPanel = false
        com.vknxworks.craftyctrl.data.lock.Lock.liveToken = null
        session = null
        loginError = reason
    }
}

// ---------------------------------------------------------------- server list

data class ServerRow(val summary: ServerSummary, val stats: ServerStats?)

class ServerListViewModel(private val api: CraftyApi) : ViewModel() {
    /** The panel address, for the "Not encrypted (HTTP)" label. */
    val baseUrl: String get() = api.baseUrl
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

    /**
     * Refreshes servers and host stats side by side. While Crafty's dashboard WebSocket is delivering both
     * ([live]), the timer's requests are thinned out to one every [LIST_LIVE_REST_MS]; [force] always asks.
     */
    suspend fun refresh(force: Boolean = false) = coroutineScope {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && live && now - lastSocketEvent < LIST_SOCKET_FRESH_MS && now - lastRest < LIST_LIVE_REST_MS) return@coroutineScope
        lastRest = now
        launch { refreshHost() }
        refreshServers()
    }

    // ------------------------------------------------------------ live stats (WebSocket)

    /** True while Crafty's dashboard WebSocket is delivering server and host stats. */
    var live by mutableStateOf(false)
        private set
    private var lastSocketEvent = 0L
    private var lastRest = 0L
    private var liveWanted = false
    private var socket: com.vknxworks.craftyctrl.data.ConsoleSocket? = null
    private var reconnect: kotlinx.coroutines.Job? = null
    private var socketFailures = 0
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** The list is on screen: open the dashboard socket, reconnecting on its own until [stopLive]. */
    fun startLive() {
        liveWanted = true
        if (socket != null || reconnect?.isActive == true) return
        connectSocket()
    }

    /** The list left the screen. */
    fun stopLive() {
        liveWanted = false
        reconnect?.cancel()
        reconnect = null
        val s = socket
        socket = null
        s?.close()
        live = false
    }

    private fun connectSocket() {
        // No token while the app is locked: nothing is opened until it unlocks.
        val token = api.currentToken()
        if (token == null) {
            scheduleReconnect()
            return
        }
        var self: com.vknxworks.craftyctrl.data.ConsoleSocket? = null
        val s = com.vknxworks.craftyctrl.data.ConsoleSocket(api.baseUrl, null, token, object : com.vknxworks.craftyctrl.data.ConsoleSocket.Listener {
            override fun onOpen() {
                mainHandler.post { if (socket === self && self != null) socketFailures = 0 }
            }

            override fun onLine(line: String) {}

            override fun onEvent(event: String, data: kotlinx.serialization.json.JsonElement) {
                mainHandler.post { if (liveWanted && socket === self && self != null) onSocketEvent(event, data) }
            }

            override fun onClosed(unauthorized: Boolean) {
                mainHandler.post {
                    if (socket !== self || self == null) return@post
                    socket = null
                    live = false
                    socketFailures++
                    if (liveWanted) scheduleReconnect()
                }
            }
        })
        self = s
        socket = s
        if (!s.connect()) {
            socket = null
            socketFailures++
            scheduleReconnect()
        }
    }

    /** Tries again after 2, 4, 8 … up to 60 seconds; the stats are polled meanwhile. */
    private fun scheduleReconnect() {
        if (!liveWanted || reconnect?.isActive == true) return
        val wait = (2_000L shl socketFailures.coerceIn(0, 5)).coerceAtMost(60_000L)
        reconnect = viewModelScope.launch {
            delay(wait)
            reconnect = null
            if (liveWanted && socket == null) connectSocket()
        }
    }

    private fun onSocketEvent(event: String, data: kotlinx.serialization.json.JsonElement) {
        when (event) {
            // A list with the stats of the servers that changed (one message per server).
            "update_server_status" -> {
                val updates = (data as? kotlinx.serialization.json.JsonArray).orEmpty()
                    .filterIsInstance<kotlinx.serialization.json.JsonObject>()
                    .mapNotNull { o -> (o["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.let { it to o } }
                    .toMap()
                if (updates.isEmpty()) return
                var changed = false
                val next = rows.map { row ->
                    val o = updates[row.summary.id] ?: return@map row
                    changed = true
                    val merged = ServerStats.merged(row.stats, o)
                    row.copy(stats = merged.copy(version = merged.version ?: fallbackVersions[row.summary.id]))
                }
                if (changed) {
                    rows = next
                    lastSocketEvent = android.os.SystemClock.elapsedRealtime()
                    live = true
                }
            }
            "update_host_stats" -> {
                if (hostDenied) return
                val o = data as? kotlinx.serialization.json.JsonObject ?: return
                val h = HostStats.merged(host, o)
                if (h.isEmpty) return
                host = h
                hostError = null
                h.cpuPercent?.let { hostCpuSamples = (hostCpuSamples + it.toFloat()).takeLast(HOST_SAMPLES) }
                h.memPercent?.let { hostMemSamples = (hostMemSamples + it.toFloat()).takeLast(HOST_SAMPLES) }
            }
        }
    }

    override fun onCleared() {
        stopLive()
        super.onCleared()
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
            fun build() = servers.zip(stats) { summary, st ->
                ServerRow(summary, st?.let { it.copy(version = it.version ?: fallbackVersions[summary.id]) })
            }
            // Versions the stats ping didn't report are looked up once per server. That takes up to two more
            // requests each, so the list is shown first and the versions are filled in when they arrive.
            val missing = servers.zip(stats).filter { (s, st) -> st?.version == null && s.id !in fallbackVersions }.map { it.first }
            rows = build()
            error = null
            if (missing.isNotEmpty()) {
                loading = false
                val found = coroutineScope {
                    missing.map { s -> async { runCatching { api.versionFallback(s.id, s.executable) }.getOrNull() } }.awaitAll()
                }
                missing.forEachIndexed { i, s -> fallbackVersions[s.id] = found[i] }
                rows = build()
            }
        } catch (e: Exception) {
            error = e.userMessage()
        } finally {
            loading = false
        }
    }

    val messages = Channel<String>(Channel.BUFFERED)
    var broadcasting by mutableStateOf(false)
        private set

    /** Sends a chat message ("say") to every running server. */
    fun broadcast(text: String, onDone: () -> Unit) {
        val msg = text.trim().replace('\n', ' ')
        if (msg.isEmpty() || broadcasting) return
        viewModelScope.launch {
            broadcasting = true
            val targets = rows.filter { it.stats?.running == true }
            val failed = targets.map { r ->
                async { r.summary.name to runCatching { api.sendCommand(r.summary.id, "say $msg") }.isSuccess }
            }.awaitAll().filter { !it.second }.map { it.first }
            broadcasting = false
            onDone()
            messages.send(
                when {
                    targets.isEmpty() -> str(R.string.no_servers_are_running)
                    failed.isEmpty() -> plural(R.plurals.sent_to_servers, targets.size, targets.size)
                    else -> str(R.string.couldn_t_send_to_x, failed.joinToString())
                },
            )
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            refreshing = true
            refresh(force = true)
            refreshing = false
        }
    }
}

private const val HOST_SAMPLES = 60
/** Socket stats older than this no longer count as live. */
private const val LIST_SOCKET_FRESH_MS = 20_000L
/** While the socket delivers the stats, a full answer is still fetched this often. */
private const val LIST_LIVE_REST_MS = 15_000L

// ---------------------------------------------------------------- server detail

enum class PowerAction(val apiName: String, @StringRes private val labelRes: Int, @StringRes private val confirmRes: Int?, val dangerous: Boolean) {
    START("start_server", R.string.power_start, null, false),
    RESTART("restart_server", R.string.power_restart, R.string.power_restart_confirm, false),
    STOP("stop_server", R.string.power_stop, R.string.power_stop_confirm, false),
    KILL("kill_server", R.string.power_kill, R.string.power_kill_confirm, true);

    val label: String get() = str(labelRes)
    val confirmText: String? get() = confirmRes?.let { str(it) }
}

data class Loadable<T>(val data: T? = null, val loading: Boolean = false, val error: String? = null)

class ServerDetailViewModel(private val api: CraftyApi, val serverId: String) : ViewModel() {
    var stats by mutableStateOf<ServerStats?>(null)
        private set
    var statsError by mutableStateOf<String?>(null)
        private set
    /** Main thread only, like everything that changes [logs]. */
    private val console = com.vknxworks.craftyctrl.data.ConsoleBuffer(MAX_CONSOLE_LINES)

    /** The console's lines. Each has an id of its own, so the list on screen can follow them as they move up. */
    var logs by mutableStateOf(console.lines)
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
                        files = files.copy(loading = false, error = str(R.string.that_s_a_file_not_a))
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
        if (!inServerFolder(folder)) return
        if (!com.vknxworks.craftyctrl.data.isSafeEntryName(name.trim())) {
            viewModelScope.launch { _messages.send(str(R.string.use_a_plain_name_without_or)) }
            return
        }
        viewModelScope.launch {
            try {
                api.createEntry(serverId, folder, name, directory)
                _messages.send(if (directory) str(R.string.folder_created) else str(R.string.file_created))
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

    /** The server's own folder or anything inside it (where new files and uploads may go). */
    private fun inServerFolder(path: String): Boolean {
        val root = rootPath?.trimEnd('/') ?: return false
        return path.trimEnd('/') == root || insideServer(path)
    }

    private fun insideServer(path: String): Boolean {
        val root = rootPath?.trimEnd('/') ?: return false
        // "root/../other" starts with the root but points outside it.
        if (com.vknxworks.craftyctrl.data.hasDotSegment(path.removePrefix(root))) return false
        return path.startsWith("$root/") && path.trimEnd('/') != root
    }

    fun renameEntry(entry: FileEntry, newName: String) {
        val folder = filePath ?: return
        if (!insideServer(entry.path)) return
        // A rename stays in the same folder: a name with a separator or ".." would move the item elsewhere.
        if (!com.vknxworks.craftyctrl.data.isSafeEntryName(newName.trim())) {
            viewModelScope.launch { _messages.send(str(R.string.use_a_plain_name_without_or)) }
            return
        }
        viewModelScope.launch {
            try {
                api.renameEntry(serverId, entry.path, newName)
                _messages.send(str(R.string.renamed_to_x, newName))
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
                        is FileResult.Folder -> throw IllegalStateException(str(R.string.folders_can_t_be_copied))
                    }
                    api.createEntry(serverId, dest, target, false)
                    api.saveFile(serverId, join(dest, target), text)
                    _messages.send(str(R.string.copied_to_x, target))
                } else {
                    val destTrim = dest.trimEnd('/')
                    when {
                        destTrim == c.fromFolder.trimEnd('/') -> {
                            _messages.send(str(R.string.it_s_already_in_this_folder))
                            return@launch
                        }
                        c.entry.isDir && (destTrim == c.entry.path.trimEnd('/') || destTrim.startsWith(c.entry.path.trimEnd('/') + "/")) -> {
                            _messages.send(str(R.string.a_folder_can_t_be_moved))
                            return@launch
                        }
                        files.data.orEmpty().any { it.name == c.entry.name } -> {
                            _messages.send(str(R.string.this_folder_already_has_an_item, c.entry.name))
                            return@launch
                        }
                    }
                    api.renameEntry(serverId, c.entry.path, relativePath(c.fromFolder, dest) + c.entry.name)
                    _messages.send(str(R.string.moved_x, c.entry.name))
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
            viewModelScope.launch { _messages.send(str(R.string.that_item_isn_t_inside_this)) }
            return
        }
        viewModelScope.launch {
            try {
                api.deleteEntry(serverId, entry.path)
                _messages.send(str(R.string.deleted_x, entry.name))
                openFolder(folder)
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            }
        }
    }

    /** Uploads a picked file into the open folder. */
    fun upload(resolver: android.content.ContentResolver, uri: android.net.Uri) {
        val folder = filePath ?: return
        if (uploadProgress != null || !inServerFolder(folder)) return
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
                    _messages.send(str(R.string.couldn_t_read_the_file_size))
                    return@launch
                }
                val input = resolver.openInputStream(uri) ?: run {
                    _messages.send(str(R.string.couldn_t_open_that_file))
                    return@launch
                }
                input.use { stream ->
                    api.upload(serverId, folder, name, size, { b, off, len -> stream.read(b, off, len) }) { p ->
                        uploadProgress = p
                    }
                }
                _messages.send(str(R.string.uploaded_x, name))
                openFolder(folder)
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                uploadProgress = null
            }
        }
    }

    // ------------------------------------------------------------ downloads to the phone

    /** Bytes received so far while a download to the phone is running, otherwise null. */
    var downloadedBytes by mutableStateOf<Long?>(null)
        private set

    /** What is being downloaded, for the progress line. */
    var downloadName by mutableStateOf("")
        private set

    private var downloadJob: kotlinx.coroutines.Job? = null

    /** Saves a file (or a folder, which Crafty zips first) from the server's folder into [target], a document the user just created. */
    fun downloadEntry(resolver: android.content.ContentResolver, target: android.net.Uri, entry: FileEntry) {
        if (!insideServer(entry.path)) return
        startDownload(resolver, target, entry.name) { sink, progress -> api.downloadFile(serverId, entry.path, sink, progress) }
    }

    /** Saves a backup archive into [target], a document the user just created. */
    fun downloadBackup(resolver: android.content.ContentResolver, target: android.net.Uri, config: BackupConfig, fileName: String) {
        val backupId = config.id
        if (backupId == null) {
            viewModelScope.launch {
                runCatching { android.provider.DocumentsContract.deleteDocument(resolver, target) }
                _messages.send(str(R.string.download_backup_needs_newer_crafty))
            }
            return
        }
        startDownload(resolver, target, fileName) { sink, progress -> api.downloadBackup(serverId, backupId, fileName, sink, progress) }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    private fun startDownload(
        resolver: android.content.ContentResolver,
        target: android.net.Uri,
        name: String,
        fetch: suspend (java.io.OutputStream, (Long) -> Unit) -> Unit,
    ) {
        if (downloadedBytes != null) {
            // One download at a time; the document that was just created for this one stays empty, so remove it.
            runCatching { android.provider.DocumentsContract.deleteDocument(resolver, target) }
            viewModelScope.launch { _messages.send(str(R.string.download_already_running)) }
            return
        }
        downloadName = name
        downloadedBytes = 0L
        downloadJob = viewModelScope.launch {
            var done = false
            try {
                val out = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { resolver.openOutputStream(target, "w") }
                if (out == null) {
                    _messages.send(str(R.string.download_cant_write))
                    return@launch
                }
                var lastShown = 0L
                out.use { sink ->
                    fetch(sink) { bytes ->
                        // Not every 64 KB piece needs to redraw the screen.
                        if (bytes - lastShown >= 512 * 1024) {
                            lastShown = bytes
                            downloadedBytes = bytes
                        }
                    }
                }
                done = true
                _messages.send(str(R.string.downloaded_x, name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if (e is CraftyException && e.httpCode in listOf(404, 405)) {
                    str(R.string.download_not_supported)
                } else {
                    e.userMessage()
                }
                _messages.send(message)
            } finally {
                downloadedBytes = null
                downloadJob = null
                // A download that failed or was cancelled leaves no half-written file behind. It is the
                // document that was created for this download a moment ago, never an existing file.
                if (!done) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { android.provider.DocumentsContract.deleteDocument(resolver, target) }
                    }
                }
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
                refreshStats(force = true)
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            }
        }
    }

    /** Shows a short message in the snackbar. */
    fun notify(text: String) {
        viewModelScope.launch { _messages.send(text) }
    }

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /**
     * Asks Crafty for the stats. While the WebSocket is delivering them ([statsLive]), the timer's requests
     * are thinned out to one every [LIVE_REST_MS]: the socket's messages have the numbers but not the
     * "waiting to start" and "updating" flags, so a full answer is still needed now and then. [force] always asks.
     */
    suspend fun refreshStats(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && statsLive && now - lastSocketStats < SOCKET_FRESH_MS && now - lastRestStats < LIVE_REST_MS) return
        try {
            val s = api.stats(serverId)
            lastRestStats = android.os.SystemClock.elapsedRealtime()
            stats = s
            statsError = null
            addLiveSample(s)
        } catch (e: Exception) {
            statsError = e.userMessage()
        }
    }

    private fun addLiveSample(s: ServerStats) {
        liveSamples = (liveSamples + HistoryPoint(
            time = System.currentTimeMillis(),
            cpu = s.cpu,
            memPercent = s.memPercent,
            players = s.online,
        )).takeLast(LIVE_SAMPLES)
    }

    // ------------------------------------------------------------ live stats (WebSocket)

    /** True while Crafty's WebSocket is delivering this server's stats; false means they're polled instead. */
    var statsLive by mutableStateOf(false)
        private set

    /** Percent of the backup that is running now, as Crafty reports it over the WebSocket; null when none is. */
    var backupPercent by mutableStateOf<Int?>(null)
        private set

    private var statsWanted = false
    private var lastSocketStats = 0L
    private var lastRestStats = 0L

    /** The server page is on screen: keep the socket open for live stats, whatever tab is showing. */
    fun startLiveStats() {
        statsWanted = true
        if (socket != null || reconnect?.isActive == true) return
        connectSocket()
    }

    /** The server page left the screen. */
    fun stopLiveStats() {
        statsWanted = false
        statsLive = false
        if (!consoleWanted) closeSocket()
    }

    private fun onSocketEvent(event: String, data: kotlinx.serialization.json.JsonElement) {
        val o = data as? kotlinx.serialization.json.JsonObject ?: return
        when (event) {
            "update_server_details" -> {
                val s = ServerStats.merged(stats, o)
                stats = s
                statsError = null
                lastSocketStats = android.os.SystemClock.elapsedRealtime()
                statsLive = true
                addLiveSample(s)
            }
            "backup_status" -> {
                val p = (o["percent"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: return
                backupPercent = if (p >= 100.0) null else p.toInt().coerceIn(0, 100)
                // A finished (or failed) backup changed the profile's status and the list of archives.
                if (p >= 100.0 && backups.data != null) viewModelScope.launch { delay(2_000); loadBackups() }
            }
        }
    }

    suspend fun refreshLogs() {
        try {
            val fetched = api.logs(serverId)
            // The same lines as on screen: nothing to redraw, and the lines keep their ids.
            if (!logs.sameText(fetched)) logs = console.replace(fetched)
            // Lines that came in over the socket before this answer are part of it already.
            pendingLines.clear()
            consoleError = null
        } catch (e: Exception) {
            consoleError = e.userMessage()
        }
    }

    // ------------------------------------------------------------ live console (WebSocket)

    /** True while the console streams over Crafty's WebSocket; false means it's polling the logs instead. */
    var consoleLive by mutableStateOf(false)
        private set

    private var socket: com.vknxworks.craftyctrl.data.ConsoleSocket? = null
    private var reconnect: kotlinx.coroutines.Job? = null
    private var consoleWanted = false
    private val socketWanted: Boolean get() = consoleWanted || statsWanted
    private var socketOpen = false
    private var socketFailures = 0
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Lines from the socket waiting to be shown (main thread only). A busy server prints hundreds of lines a
     * second; adding them to [logs] one by one redraws the console for every line, so they're added together
     * a few times a second instead.
     */
    private val pendingLines = ArrayList<String>()
    private var flushPosted = false
    private val flushLines = Runnable {
        flushPosted = false
        if (pendingLines.isNotEmpty()) {
            // Added to the end of the buffer; only the last MAX_CONSOLE_LINES stay in view.
            logs = console.append(pendingLines)
            pendingLines.clear()
        }
    }

    /** The console tab is showing: connect the live console, reconnecting on its own until [stopConsole]. */
    fun startConsole() {
        consoleWanted = true
        if (socket != null) {
            // The socket is already open for the live stats: the console is live from now on.
            if (socketOpen && !consoleLive) {
                consoleLive = true
                viewModelScope.launch { refreshLogs() }
            }
            return
        }
        if (reconnect?.isActive == true) return
        connectSocket()
    }

    /** The console tab is gone or the app left the screen. */
    fun stopConsole() {
        consoleWanted = false
        consoleLive = false
        if (!statsWanted) closeSocket()
    }

    private fun closeSocket() {
        reconnect?.cancel()
        reconnect = null
        val s = socket
        socket = null
        socketOpen = false
        s?.close()
        consoleLive = false
        statsLive = false
    }

    private fun connectSocket() {
        // No token while the app is locked: nothing is opened until it unlocks.
        val token = api.currentToken()
        if (token == null) {
            scheduleReconnect()
            return
        }
        // Callbacks of a socket that was already replaced or closed (they're posted, so they can arrive late)
        // are ignored: acting on them would drop the newer socket without closing it and keep it streaming.
        var self: com.vknxworks.craftyctrl.data.ConsoleSocket? = null
        val s = com.vknxworks.craftyctrl.data.ConsoleSocket(api.baseUrl, serverId, token, object : com.vknxworks.craftyctrl.data.ConsoleSocket.Listener {
            override fun onOpen() {
                mainHandler.post {
                    if (socketWanted && socket === self && self != null) {
                        socketFailures = 0
                        socketOpen = true
                        if (consoleWanted) {
                            consoleLive = true
                            // Fill in anything that was printed while the socket was down.
                            viewModelScope.launch { refreshLogs() }
                        }
                    }
                }
            }

            override fun onEvent(event: String, data: kotlinx.serialization.json.JsonElement) {
                mainHandler.post {
                    if (statsWanted && socket === self && self != null) onSocketEvent(event, data)
                }
            }

            override fun onLine(line: String) {
                mainHandler.post {
                    if (consoleWanted && socket === self && self != null) {
                        pendingLines.add(line)
                        if (!flushPosted) {
                            flushPosted = true
                            mainHandler.postDelayed(flushLines, CONSOLE_FLUSH_MS)
                        }
                    }
                }
            }

            override fun onClosed(unauthorized: Boolean) {
                mainHandler.post {
                    if (socket !== self || self == null) return@post
                    socket = null
                    socketOpen = false
                    consoleLive = false
                    statsLive = false
                    socketFailures++
                    if (socketWanted) scheduleReconnect()
                }
            }
        })
        self = s
        socket = s
        if (!s.connect()) {
            socket = null
            socketFailures++
            scheduleReconnect()
        }
    }

    /** Tries again after 2, 4, 8 … up to 60 seconds; the logs are polled meanwhile. */
    private fun scheduleReconnect() {
        if (!socketWanted || reconnect?.isActive == true) return
        val wait = (2_000L shl socketFailures.coerceIn(0, 5)).coerceAtMost(60_000L)
        reconnect = viewModelScope.launch {
            delay(wait)
            reconnect = null
            if (socketWanted && socket == null) connectSocket()
        }
    }

    override fun onCleared() {
        consoleWanted = false
        statsWanted = false
        closeSocket()
        mainHandler.removeCallbacks(flushLines)
        super.onCleared()
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
                _messages.send(str(R.string.couldn_t_write_eula_txt_x, e.userMessage()))
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
                _messages.send(str(R.string.x_sent, action.label))
                // The progress notification follows a start or a stop until Crafty reports the new state.
                val kind = when (action) {
                    PowerAction.START -> com.vknxworks.craftyctrl.monitor.ActionProgress.Kind.START
                    PowerAction.STOP -> com.vknxworks.craftyctrl.monitor.ActionProgress.Kind.STOP
                    else -> null
                }
                if (kind != null) {
                    com.vknxworks.craftyctrl.monitor.ActionProgress.watch(
                        com.vknxworks.craftyctrl.AppStrings.context, api, serverId, info?.name ?: serverId, kind,
                    )
                }
                delay(1500)
                refreshStats(force = true)
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
                // The live console shows the reply as it comes; polling picks it up from the logs.
                if (!consoleLive) {
                    delay(600)
                    refreshLogs()
                }
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
                _messages.send(str(R.string.backup_started_x, config?.name ?: str(R.string.backup_default)))
                com.vknxworks.craftyctrl.monitor.ActionProgress.watch(
                    com.vknxworks.craftyctrl.AppStrings.context, api, serverId, info?.name ?: serverId,
                    com.vknxworks.craftyctrl.monitor.ActionProgress.Kind.BACKUP, config?.id,
                )
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
                _messages.send(if (existing == null) str(R.string.schedule_created) else str(R.string.schedule_saved))
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
                _messages.send(str(R.string.schedule_deleted))
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
                _messages.send(str(R.string.backup_settings_saved))
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

    // ------------------------------------------------------------ warned restarts and stops

    /** Restarts or stops after [seconds], warning players in chat as it counts down. */
    fun startCountdown(action: PowerAction, seconds: Int, note: String?) {
        Countdowns.start(api, serverId, action, seconds, note) { err ->
            viewModelScope.launch { _messages.send(err) }
        }
        viewModelScope.launch { _messages.send(str(R.string.players_warned_x_in_x, action.label.lowercase(), Countdowns.human(seconds))) }
    }

    fun cancelCountdown() {
        Countdowns.cancel(api, serverId)
        viewModelScope.launch { _messages.send(str(R.string.cancelled)) }
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
                _messages.send(str(R.string.ran_x, task.name))
                delay(1200)
                refreshStats(force = true)
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
                _messages.send(str(R.string.extracting_x, entry.name))
                api.unzip(serverId, entry.path)
                _messages.send(str(R.string.extracted_x, entry.name))
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
            throw CraftyException(0, "STILL_RUNNING", str(R.string.the_server_didn_t_stop_within))
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
                _messages.send(str(R.string.update_started_crafty_downloads_the_new))
                delay(1500)
                refreshStats(force = true)
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
                    _messages.send(str(R.string.clone_started_it_appears_in_the))
                } else {
                    val name = runCatching { api.serverInfo(id)?.name }.getOrNull() ?: str(R.string.copy_of_x, info?.name.orEmpty())
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
            val location = config.details.firstOrNull { it.first == str(R.string.backup_field_location) }?.second
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
                _messages.send(str(R.string.restoring_x_start_the_server_when, fileName))
                onDone()
                delay(1500)
                refreshStats(force = true)
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
        /** About what Crafty's own console keeps. */
        private const val MAX_CONSOLE_LINES = 1000
        /** How long live console lines are collected before they're shown together. */
        private const val CONSOLE_FLUSH_MS = 50L
        /** Socket stats older than this no longer count as live. */
        private const val SOCKET_FRESH_MS = 20_000L
        /** While the socket delivers the stats, a full answer is still fetched this often. */
        private const val LIVE_REST_MS = 15_000L
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
