package com.vknxworks.craftyctrl.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import androidx.annotation.StringRes
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str

// ---------------------------------------------------------------- Crafty's server config

/** A server's own settings in Crafty (GET /servers/{id}), as opposed to server.properties. */
data class ServerConfig(
    val name: String,
    val autoStart: Boolean,
    val autoStartDelay: Int?,
    val crashDetection: Boolean,
    val executionCommand: String?,
    val stopCommand: String?,
    val shutdownTimeout: Int?,
    val logsDeleteAfter: Int?,
    val serverIp: String?,
    val serverPort: Int?,
    val showStatus: Boolean?,
    val countPlayers: Boolean?,
    val updateUrl: String?,
    val updateAvailable: Boolean,
    val updating: Boolean,
    val backingUp: Boolean?,
    val lastBackup: String?,
) {
    companion object {
        fun from(root: JsonElement): ServerConfig? {
            val r = root as? JsonObject ?: return null
            val d = r.obj("data") ?: r
            // Crafty nests live status flags under "status" in some versions and flat in others.
            val st = JsonObject(d + (d.obj("status") ?: emptyMap()))
            return ServerConfig(
                name = d.str("server_name").orEmpty(),
                autoStart = d.bool("auto_start") ?: false,
                autoStartDelay = d.int("auto_start_delay"),
                crashDetection = d.bool("crash_detection") ?: false,
                executionCommand = d.str("execution_command"),
                stopCommand = d.str("stop_command"),
                shutdownTimeout = d.int("shutdown_timeout"),
                logsDeleteAfter = d.int("logs_delete_after"),
                serverIp = d.str("server_ip"),
                serverPort = d.int("server_port"),
                showStatus = d.bool("show_status"),
                countPlayers = d.bool("count_players"),
                updateUrl = d.str("executable_update_url")?.trim()?.takeIf { it.isNotEmpty() },
                updateAvailable = st.bool("update_available") ?: false,
                updating = st.bool("updating") ?: false,
                backingUp = st.bool("backing_up"),
                lastBackup = st.str("last_backup")?.trim()?.takeIf { it.isNotEmpty() && !it.equals("none", true) },
            )
        }
    }
}

private val xms = Regex("""-Xms(\d+)([kKmMgG]?)(?=\s|"|$)""")
private val xmx = Regex("""-Xmx(\d+)([kKmMgG]?)(?=\s|"|$)""")
private val javaWord = Regex("""\bjava(\.exe)?"?\s""")

/** Whether the app can set memory in this start command (it runs java directly). */
fun canSetMemory(command: String?): Boolean =
    command != null && (xms.containsMatchIn(command) || xmx.containsMatchIn(command) || javaWord.containsMatchIn(command))

private fun toMb(n: String, unit: String): Int? {
    val v = n.toLongOrNull() ?: return null
    return when (unit.lowercase()) {
        "k" -> (v / 1024).toInt()
        "m" -> v.toInt()
        "g" -> (v * 1024).toInt()
        "" -> (v / (1024 * 1024)).toInt() // plain bytes
        else -> null
    }
}

/** Min and max Java heap in MB from a start command like "java -Xms2000M -Xmx4000M -jar ...". */
fun memoryOf(command: String?): Pair<Int?, Int?> {
    if (command == null) return null to null
    val min = xms.find(command)?.let { toMb(it.groupValues[1], it.groupValues[2]) }
    val max = xmx.find(command)?.let { toMb(it.groupValues[1], it.groupValues[2]) }
    return min to max
}

/** The start command with its heap flags set to [minMb] and [maxMb], adding them after "java" if missing. */
fun withMemory(command: String, minMb: Int, maxMb: Int): String {
    var c = command
    c = if (xms.containsMatchIn(c)) c.replace(xms, "-Xms${minMb}M") else c.replaceFirst(javaWord, "$0-Xms${minMb}M ")
    c = if (xmx.containsMatchIn(c)) c.replace(xmx, "-Xmx${maxMb}M") else c.replaceFirst(Regex("""-Xms\d+M(?=\s|"|$)"""), "$0 -Xmx${maxMb}M")
    return c
}

// ---------------------------------------------------------------- player lists

/** The player lists a server keeps as JSON files next to server.properties. */
enum class PlayerList(@StringRes private val titleRes: Int) {
    WHITELIST(R.string.list_whitelist),
    OPS(R.string.list_operators),
    BANNED_PLAYERS(R.string.list_banned_players),
    BANNED_IPS(R.string.list_banned_ips);

    val title: String get() = str(titleRes)

    fun fileName(bedrock: Boolean): String? = when (this) {
        WHITELIST -> if (bedrock) "allowlist.json" else "whitelist.json"
        OPS -> if (bedrock) "permissions.json" else "ops.json"
        BANNED_PLAYERS -> if (bedrock) null else "banned-players.json"
        BANNED_IPS -> if (bedrock) null else "banned-ips.json"
    }

    fun titleFor(bedrock: Boolean) = if (this == WHITELIST && bedrock) str(R.string.list_allowlist) else title

    companion object {
        fun forServer(bedrock: Boolean): List<PlayerList> =
            if (bedrock) listOf(WHITELIST, OPS) else entries
    }
}

/** One row of a player list file. Unknown fields are kept in [raw] so saving doesn't drop them. */
data class PlayerEntry(
    val name: String?,
    val id: String?,
    val ip: String?,
    val detail: String?,
    val raw: JsonObject,
) {
    val label: String get() = name ?: ip ?: id ?: str(R.string.unknown)
}

/** Null when the file isn't a JSON list, so callers never mistake a broken file for an empty one. */
fun parsePlayerList(text: String, list: PlayerList, bedrock: Boolean): List<PlayerEntry>? {
    val arr = runCatching { lenientJson.parseToJsonElement(text.ifBlank { "[]" }) }.getOrNull() as? JsonArray
        ?: return null
    return arr.filterIsInstance<JsonObject>().map { o ->
        val detail = when (list) {
            PlayerList.OPS -> if (bedrock) o.str("permission")?.replaceFirstChar { it.uppercase() } else o.str("level")?.let { str(R.string.level_x, it) }
            PlayerList.BANNED_PLAYERS, PlayerList.BANNED_IPS -> listOfNotNull(
                o.str("reason")?.takeIf { it.isNotBlank() },
                o.str("expires")?.takeIf { it.isNotBlank() && !it.equals("forever", true) }?.let { "until $it" },
            ).joinToString(" · ").ifEmpty { null }
            PlayerList.WHITELIST -> null
        }
        PlayerEntry(
            name = o.str("name")?.takeIf { it.isNotBlank() },
            id = (o.str("uuid") ?: o.str("xuid"))?.takeIf { it.isNotBlank() },
            ip = o.str("ip")?.takeIf { it.isNotBlank() },
            detail = detail,
            raw = o,
        )
    }
}

/** Pretty-printed JSON array, like the files the server writes. */
fun renderPlayerList(entries: List<PlayerEntry>): String {
    if (entries.isEmpty()) return "[]\n"
    val pretty = kotlinx.serialization.json.Json { prettyPrint = true }
    return pretty.encodeToString(JsonArray.serializer(), JsonArray(entries.map { it.raw })) + "\n"
}

private val banDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

/** A new file row, shaped like the ones the server writes itself. */
fun newPlayerEntry(list: PlayerList, bedrock: Boolean, name: String, id: String?, reason: String?): PlayerEntry {
    val raw = buildJsonObject {
        when (list) {
            PlayerList.WHITELIST -> if (bedrock) {
                put("ignoresPlayerLimit", false)
                put("name", name)
            } else {
                put("uuid", id.orEmpty())
                put("name", name)
            }
            PlayerList.OPS -> {
                // Bedrock's permissions.json needs an Xbox ID, which only the running server can look up.
                require(!bedrock) { str(R.string.bedrock_ops_need_running) }
                put("uuid", id.orEmpty())
                put("name", name)
                put("level", 4)
                put("bypassesPlayerLimit", false)
            }
            PlayerList.BANNED_PLAYERS, PlayerList.BANNED_IPS -> {
                if (list == PlayerList.BANNED_IPS) put("ip", name) else {
                    put("uuid", id.orEmpty())
                    put("name", name)
                }
                put("created", banDate.format(Date()))
                put("source", "CraftyCtrl")
                put("expires", "forever")
                put("reason", reason?.takeIf { it.isNotBlank() } ?: "Banned by an operator.")
            }
        }
    }
    return PlayerEntry(name.takeIf { list != PlayerList.BANNED_IPS }, id, name.takeIf { list == PlayerList.BANNED_IPS }, null, raw)
}

/** The UUID an offline-mode (online-mode=false) server gives a player name. */
fun offlineUuid(name: String): String =
    UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8)).toString()

/** "069a79f444e94726a5befca90e38aaf5" -> "069a79f4-44e9-4726-a5be-fca90e38aaf5". */
fun dashedUuid(hex: String): String =
    if (hex.length == 32) "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}" else hex

/** Console commands that change a list on a running server, so it applies without a restart. */
fun listCommand(list: PlayerList, bedrock: Boolean, add: Boolean, who: String, reason: String? = null): String = when (list) {
    PlayerList.WHITELIST -> "${if (bedrock) "allowlist" else "whitelist"} ${if (add) "add" else "remove"} ${quoteName(who, bedrock)}"
    PlayerList.OPS -> "${if (add) "op" else "deop"} ${quoteName(who, bedrock)}"
    PlayerList.BANNED_PLAYERS -> if (add) "ban $who" + reason?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty() else "pardon $who"
    PlayerList.BANNED_IPS -> if (add) "ban-ip $who" + reason?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty() else "pardon-ip $who"
}

/** Bedrock gamertags can contain spaces and must then be quoted. */
private fun quoteName(name: String, bedrock: Boolean) = if (bedrock && ' ' in name) "\"$name\"" else name

internal val lenientJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

/** File names of backups found anywhere in a reply (strings ending in .zip, .tar.gz, ...). */
internal fun backupNamesIn(e: JsonElement?): List<String> {
    val out = linkedSetOf<String>()
    fun walk(x: JsonElement?) {
        when (x) {
            is JsonArray -> x.forEach(::walk)
            is JsonObject -> {
                (x.str("filename") ?: x.str("name") ?: x.str("path"))?.let { if (looksLikeBackup(it)) out += it.substringAfterLast('/') }
                // Skip settings such as excluded folders, which can contain .zip paths that aren't backups.
                x.forEach { (k, v) -> if (!k.contains("exclu", ignoreCase = true)) walk(v) }
            }
            is JsonPrimitive -> if (x.isString && looksLikeBackup(x.content)) out += x.content.substringAfterLast('/')
            else -> Unit
        }
    }
    walk(e)
    return out.toList()
}

private fun looksLikeBackup(s: String) =
    s.length < 300 && Regex("""\.(zip|tar|tar\.gz|tgz|7z)$""", RegexOption.IGNORE_CASE).containsMatchIn(s)
