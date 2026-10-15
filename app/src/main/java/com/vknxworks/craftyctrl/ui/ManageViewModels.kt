package com.vknxworks.craftyctrl.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vknxworks.craftyctrl.data.BEDROCK_PROPERTIES
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.CraftyException
import com.vknxworks.craftyctrl.data.JAVA_PROPERTIES
import com.vknxworks.craftyctrl.data.PlayerEntry
import com.vknxworks.craftyctrl.data.PlayerList
import com.vknxworks.craftyctrl.data.PropDef
import com.vknxworks.craftyctrl.data.PropertiesFile
import com.vknxworks.craftyctrl.data.ServerConfig
import com.vknxworks.craftyctrl.data.ServerSummary
import com.vknxworks.craftyctrl.data.escapeProp
import com.vknxworks.craftyctrl.data.join
import com.vknxworks.craftyctrl.data.listCommand
import com.vknxworks.craftyctrl.data.newPlayerEntry
import com.vknxworks.craftyctrl.data.offlineUuid
import com.vknxworks.craftyctrl.data.parsePlayerList
import com.vknxworks.craftyctrl.data.renderPlayerList
import com.vknxworks.craftyctrl.data.unescapeProp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

private fun Exception.msg(): String {
    if (this is CancellationException) throw this
    return message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
}

/** Shared bits for the management screens: the server's folder/type, whether it runs, and snackbar messages. */
abstract class ManageBase(protected val api: CraftyApi, val serverId: String) : ViewModel() {
    var info by mutableStateOf<ServerSummary?>(null)
        protected set
    var running by mutableStateOf<Boolean?>(null)
        protected set

    protected val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val isBedrock: Boolean get() = info?.isBedrock == true

    protected suspend fun loadInfo(): ServerSummary {
        val i = info ?: api.serverInfo(serverId)?.also { info = it }
            ?: throw CraftyException(0, "NO_SERVER", "Couldn't load this server.")
        running = runCatching { api.stats(serverId).running }.getOrNull()
        return i
    }

    protected fun folder(): String = info?.path
        ?: throw CraftyException(0, "NO_PATH", "Crafty didn't say where this server's files are.")

    fun restartServer() {
        viewModelScope.launch {
            try {
                api.action(serverId, "restart_server")
                _messages.send("Restarting the server")
            } catch (e: Exception) {
                _messages.send(e.msg())
            }
        }
    }
}

// ---------------------------------------------------------------- server.properties

class PropertiesViewModel(api: CraftyApi, serverId: String) : ManageBase(api, serverId) {
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    private var file: PropertiesFile? = null
    /** Values as stored in the file, unescaped for display. */
    var original by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    /** Values the user changed and hasn't saved. */
    val edits = mutableStateMapOf<String, String>()

    val defs: List<PropDef> get() = if (isBedrock) BEDROCK_PROPERTIES else JAVA_PROPERTIES

    /** Keys in the file that aren't in the known list, so they're still editable. */
    val unknownKeys: List<String> get() = original.keys.filter { k -> defs.none { it.key == k } }.sorted()

    val dirty: Boolean get() = edits.any { (k, v) -> original[k] != v }

    fun value(def: PropDef): String = edits[def.key] ?: original[def.key] ?: def.default
    fun value(key: String): String = edits[key] ?: original[key].orEmpty()
    fun isSet(key: String): Boolean = key in original

    fun set(key: String, v: String) {
        edits[key] = v
    }

    fun discard() = edits.clear()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                loadInfo()
                val text = api.readText(serverId, join(folder(), "server.properties"))
                if (text == null) {
                    error = "This server has no server.properties yet. Start it once and the server creates one."
                } else {
                    val f = PropertiesFile(text)
                    file = f
                    original = f.values.mapValues { (_, v) -> if (isBedrock) v else unescapeProp(v) }
                    edits.clear()
                }
            } catch (e: Exception) {
                error = e.msg()
            } finally {
                loading = false
            }
        }
    }

    /** Writes only the changed keys back, keeping comments and order. [onSaved] gets whether a restart is needed. */
    fun save(onSaved: (needsRestart: Boolean) -> Unit) {
        val f = file ?: return
        if (saving) return
        val changes = edits.filter { (k, v) -> original[k] != v }
            .mapValues { (_, v) -> if (isBedrock) v.replace("\n", " ") else escapeProp(v) }
        if (changes.isEmpty()) return
        viewModelScope.launch {
            saving = true
            try {
                // Re-read first so changes made since opening (e.g. by the server) aren't overwritten.
                val latest = api.readText(serverId, join(folder(), "server.properties"))?.let(::PropertiesFile)
                    ?: throw CraftyException(0, "READ_FAILED", "Couldn't read server.properties just now. Nothing was changed.")
                api.saveFile(serverId, join(folder(), "server.properties"), latest.render(changes))
                file = PropertiesFile(latest.render(changes))
                original = file!!.values.mapValues { (_, v) -> if (isBedrock) v else unescapeProp(v) }
                edits.clear()
                running = runCatching { api.stats(serverId).running }.getOrNull()
                onSaved(running == true)
            } catch (e: Exception) {
                _messages.send(e.msg())
            } finally {
                saving = false
            }
        }
    }
}

// ---------------------------------------------------------------- whitelist, ops, bans

class PlayersViewModel(api: CraftyApi, serverId: String) : ManageBase(api, serverId) {
    val lists = mutableStateMapOf<PlayerList, Loadable<List<PlayerEntry>>>()
    var loadError by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    /** white-list / allow-list from server.properties; null if unknown. */
    var whitelistOn by mutableStateOf<Boolean?>(null)
        private set
    private var onlineMode = true

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            loadError = null
            try {
                loadInfo()
            } catch (e: Exception) {
                loadError = e.msg()
                return@launch
            }
            readProperties()
            PlayerList.forServer(isBedrock).forEach { load(it) }
        }
    }

    private suspend fun readProperties() {
        val text = runCatching { api.readText(serverId, join(folder(), "server.properties")) }.getOrNull() ?: return
        val v = PropertiesFile(text).values
        whitelistOn = v[if (isBedrock) "allow-list" else "white-list"]?.toBooleanStrictOrNull()
        onlineMode = v["online-mode"]?.toBooleanStrictOrNull() ?: true
    }

    private suspend fun load(list: PlayerList) {
        val name = list.fileName(isBedrock) ?: return
        lists[list] = (lists[list] ?: Loadable()).copy(loading = true, error = null)
        lists[list] = try {
            val text = api.readText(serverId, join(folder(), name))
            val parsed = parsePlayerList(text.orEmpty(), list, isBedrock)
                ?: throw CraftyException(0, "BAD_FILE", "$name isn't valid JSON, so the app won't change it. Fix it in the Files tab.")
            Loadable(parsed)
        } catch (e: Exception) {
            Loadable(lists[list]?.data, error = e.msg())
        }
    }

    /**
     * Applies [change] to the list file as it is on the server right now, never to a stale or
     * partial copy, so a failed read can't wipe the list.
     */
    private suspend fun edit(list: PlayerList, change: (List<PlayerEntry>) -> List<PlayerEntry>) {
        val name = list.fileName(isBedrock) ?: return
        val loaded = lists[list]
        if (loaded?.data == null || loaded.error != null) {
            throw CraftyException(0, "NOT_LOADED", "The ${list.titleFor(isBedrock).lowercase()} hasn't loaded, so it can't be changed safely. Reload and try again.")
        }
        val text = api.readText(serverId, join(folder(), name))
        if (text == null && loaded.data.isNotEmpty()) {
            throw CraftyException(0, "READ_FAILED", "Couldn't read $name just now. Nothing was changed.")
        }
        val fresh = parsePlayerList(text.orEmpty(), list, isBedrock)
            ?: throw CraftyException(0, "BAD_FILE", "$name isn't valid JSON, so the app won't change it.")
        api.writeText(serverId, folder(), name, renderPlayerList(change(fresh)), exists = text != null)
    }

    /** Java needs a UUID to list a player in the files; online servers use the Mojang account's. */
    private suspend fun uuidFor(name: String): String {
        if (!onlineMode) return offlineUuid(name)
        return CraftyApi.mojangUuid(name)
            ?: throw CraftyException(0, "NO_PLAYER", "There's no Minecraft account named $name.")
    }

    fun add(list: PlayerList, rawName: String, reason: String?) {
        val name = rawName.trim()
        if (name.isEmpty() || busy) return
        val current = lists[list]?.data.orEmpty()
        if (current.any { it.label.equals(name, ignoreCase = true) }) {
            viewModelScope.launch { _messages.send("$name is already on the list.") }
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                running = runCatching { api.stats(serverId).running }.getOrNull()
                if (running == true) {
                    // A running server owns these files; the console applies it now and saves it.
                    api.sendCommand(serverId, listCommand(list, isBedrock, add = true, who = name, reason = reason))
                    _messages.send("Sent to the server")
                    delay(1500)
                } else {
                    val id = when {
                        list == PlayerList.BANNED_IPS -> null
                        isBedrock && list == PlayerList.OPS -> throw CraftyException(
                            0, "NEEDS_RUNNING",
                            "Bedrock operators are stored by Xbox ID. Start the server to op someone by name.",
                        )
                        isBedrock -> null
                        else -> uuidFor(name)
                    }
                    edit(list) { fresh ->
                        if (fresh.any { it.label.equals(name, ignoreCase = true) }) fresh
                        else fresh + newPlayerEntry(list, isBedrock, name, id, reason)
                    }
                    _messages.send("Added $name. It takes effect when the server starts.")
                }
                load(list)
            } catch (e: Exception) {
                _messages.send(e.msg())
            } finally {
                busy = false
            }
        }
    }

    fun remove(list: PlayerList, entry: PlayerEntry) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                running = runCatching { api.stats(serverId).running }.getOrNull()
                val who = if (list == PlayerList.BANNED_IPS) entry.ip else entry.name
                if (running == true && who != null) {
                    api.sendCommand(serverId, listCommand(list, isBedrock, add = false, who = who))
                    _messages.send("Sent to the server")
                    delay(1500)
                } else {
                    edit(list) { fresh -> fresh.filter { it.raw != entry.raw } }
                    _messages.send(
                        if (running == true) "Removed. Restart the server to apply it." else "Removed ${entry.label}",
                    )
                }
                load(list)
            } catch (e: Exception) {
                _messages.send(e.msg())
            } finally {
                busy = false
            }
        }
    }

    fun setWhitelist(on: Boolean) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                running = runCatching { api.stats(serverId).running }.getOrNull()
                if (running == true) {
                    api.sendCommand(serverId, "${if (isBedrock) "allowlist" else "whitelist"} ${if (on) "on" else "off"}")
                }
                // Java saves this itself when running; Bedrock and stopped servers need the file changed.
                if (running != true || isBedrock) {
                    val path = join(folder(), "server.properties")
                    val text = api.readText(serverId, path)
                        ?: throw CraftyException(0, "NO_PROPS", "This server has no server.properties yet.")
                    api.saveFile(serverId, path, PropertiesFile(text).render(mapOf((if (isBedrock) "allow-list" else "white-list") to on.toString())))
                }
                whitelistOn = on
                _messages.send(if (on) "${if (isBedrock) "Allowlist" else "Whitelist"} on" else "${if (isBedrock) "Allowlist" else "Whitelist"} off")
            } catch (e: Exception) {
                _messages.send(e.msg())
            } finally {
                busy = false
            }
        }
    }
}

// ---------------------------------------------------------------- Crafty server options

class OptionsViewModel(api: CraftyApi, serverId: String) : ManageBase(api, serverId) {
    var config by mutableStateOf(Loadable<ServerConfig>())
        private set
    var saving by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            config = config.copy(loading = true, error = null)
            config = try {
                runCatching { loadInfo() }
                Loadable(api.serverConfig(serverId))
            } catch (e: Exception) {
                config.copy(loading = false, error = e.msg())
            }
        }
    }

    fun save(fields: JsonObject, onDone: () -> Unit) {
        if (saving || fields.isEmpty()) return
        viewModelScope.launch {
            saving = true
            try {
                api.updateServer(serverId, fields)
                _messages.send("Server options saved")
                config = Loadable(api.serverConfig(serverId))
                onDone()
            } catch (e: Exception) {
                _messages.send(e.msg())
            } finally {
                saving = false
            }
        }
    }
}

// ---------------------------------------------------------------- plugins & mods

/** A plugin or mod jar; disabled ones are renamed to end in ".disabled" so the server skips them. */
data class Addon(val fileName: String, val folder: String, val path: String) {
    val enabled: Boolean get() = !fileName.endsWith(".disabled", ignoreCase = true)
    val name: String get() = fileName.removeSuffix(".disabled").removeSuffix(".jar")
}

class AddonsViewModel(api: CraftyApi, serverId: String) : ManageBase(api, serverId) {
    /** Folder name ("plugins" / "mods") to its jars; only folders that exist are listed. */
    var folders by mutableStateOf<Map<String, List<Addon>>>(emptyMap())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var uploadProgress by mutableStateOf<Float?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                loadInfo()
                val found = linkedMapOf<String, List<Addon>>()
                for (name in listOf("plugins", "mods")) {
                    val dir = join(folder(), name)
                    val r = runCatching { api.browse(serverId, dir) }.getOrNull() as? com.vknxworks.craftyctrl.data.FileResult.Folder ?: continue
                    found[name] = r.entries
                        .filter { !it.isDir && (it.name.endsWith(".jar", true) || it.name.endsWith(".jar.disabled", true)) }
                        .map { Addon(it.name, dir, it.path) }
                        .sortedBy { it.name.lowercase() }
                }
                folders = found
            } catch (e: Exception) {
                error = e.msg()
            } finally {
                loading = false
            }
        }
    }

    fun setEnabled(a: Addon, on: Boolean) {
        if (busy != null || a.enabled == on) return
        val newName = if (on) a.fileName.removeSuffix(".disabled") else a.fileName + ".disabled"
        viewModelScope.launch {
            busy = a.path
            try {
                api.renameEntry(serverId, a.path, newName)
                _messages.send("${a.name} ${if (on) "enabled" else "disabled"}. Restart the server to apply.")
                load()
            } catch (e: Exception) {
                _messages.send(e.msg())
            } finally {
                busy = null
            }
        }
    }

    fun delete(a: Addon) {
        if (busy != null) return
        viewModelScope.launch {
            busy = a.path
            try {
                api.deleteEntry(serverId, a.path)
                _messages.send("Removed ${a.name}. Restart the server to apply.")
                load()
            } catch (e: Exception) {
                _messages.send(e.msg())
            } finally {
                busy = null
            }
        }
    }

    /** Uploads a picked jar into [folderName] ("plugins" or "mods"), creating the folder if needed. */
    fun upload(folderName: String, resolver: android.content.ContentResolver, uri: android.net.Uri) {
        if (uploadProgress != null) return
        viewModelScope.launch {
            uploadProgress = 0f
            try {
                var name = "upload.jar"
                var size = -1L
                resolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sz = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (n >= 0) name = c.getString(n) ?: name
                        if (sz >= 0 && !c.isNull(sz)) size = c.getLong(sz)
                    }
                }
                if (!name.endsWith(".jar", true)) {
                    _messages.send("Pick a .jar file.")
                    return@launch
                }
                if (size < 0) throw CraftyException(0, "SIZE", "Couldn't read the file size.")
                if (folderName !in folders) runCatching { api.createEntry(serverId, folder(), folderName, true) }
                val input = resolver.openInputStream(uri) ?: throw CraftyException(0, "OPEN", "Couldn't open that file.")
                input.use { stream ->
                    api.upload(serverId, join(folder(), folderName), name, size, { b, off, len -> stream.read(b, off, len) }) { p ->
                        uploadProgress = p
                    }
                }
                _messages.send("Added ${name.removeSuffix(".jar")}. Restart the server to load it.")
                load()
            } catch (e: Exception) {
                _messages.send(e.msg())
            } finally {
                uploadProgress = null
            }
        }
    }
}

// ---------------------------------------------------------------- logs

data class LogFile(val name: String, val path: String) {
    /** Rotated logs are gzipped; Crafty can't show their contents as text. */
    val compressed: Boolean get() = name.endsWith(".gz", ignoreCase = true)
}

class LogsViewModel(api: CraftyApi, serverId: String) : ManageBase(api, serverId) {
    var files by mutableStateOf(Loadable<List<LogFile>>())
        private set
    var open by mutableStateOf<LogFile?>(null)
        private set
    var lines by mutableStateOf(Loadable<List<String>>())
        private set

    init {
        loadList()
    }

    fun loadList() {
        viewModelScope.launch {
            files = files.copy(loading = true, error = null)
            files = try {
                loadInfo()
                val dir = join(folder(), "logs")
                val r = api.browse(serverId, dir) as? com.vknxworks.craftyctrl.data.FileResult.Folder
                    ?: throw CraftyException(0, "NO_LOGS", "This server has no logs folder.")
                // latest.log first, then newest dated files (their names sort by date).
                val list = r.entries.filter { !it.isDir }.map { LogFile(it.name, it.path) }
                    .sortedWith(compareBy<LogFile> { it.name != "latest.log" }.thenByDescending { it.name })
                Loadable(list)
            } catch (e: Exception) {
                files.copy(loading = false, error = e.msg())
            }
        }
    }

    fun openFile(f: LogFile?) {
        open = f
        if (f == null) return
        viewModelScope.launch {
            lines = Loadable(loading = true)
            lines = try {
                val text = api.readText(serverId, f.path)
                    ?: throw CraftyException(0, "READ", "Couldn't read ${f.name}.")
                // Very long logs are cut to their last lines so the phone stays responsive.
                Loadable(text.lines().dropLastWhile { it.isEmpty() }.takeLast(MAX_LINES))
            } catch (e: Exception) {
                Loadable(error = e.msg())
            }
        }
    }

    companion object {
        const val MAX_LINES = 20_000
    }
}
