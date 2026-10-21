package com.vknxworks.craftyctrl.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import androidx.annotation.StringRes
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str
import com.vknxworks.craftyctrl.plural

// Crafty's responses vary between versions (numbers sent as strings, "True"/"False",
// lists sent as Python reprs), so everything is read leniently from JsonElement trees.

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.bool(key: String): Boolean? = when (str(key)?.trim()?.lowercase()) {
    "true", "1", "yes" -> true
    "false", "0", "no" -> false
    else -> null
}

internal fun JsonObject.num(key: String): Double? =
    str(key)?.replace("%", "")?.trim()?.toDoubleOrNull()

internal fun JsonObject.int(key: String): Int? = num(key)?.toInt()

/** `data` as a list of elements, or empty. */
internal fun JsonElement.dataArray(): List<JsonElement> =
    ((this as? JsonObject)?.get("data") as? JsonArray) ?: emptyList()

/** `data` as a list of objects, whether Crafty sent an array or a map keyed by id. */
internal fun JsonElement.dataItems(): List<JsonObject> =
    when (val d = (this as? JsonObject)?.get("data")) {
        is JsonArray -> d.filterIsInstance<JsonObject>()
        is JsonObject -> {
            val nested = d.values.filterIsInstance<JsonObject>()
            if (nested.isEmpty() && d.isNotEmpty()) listOf(d) else nested
        }
        else -> emptyList()
    }

/**
 * Backup configs as objects, whatever wrapper Crafty uses: a list, a map keyed by id,
 * a list nested under a key like "backups", or plain names.
 */
internal fun JsonElement.backupItems(): List<JsonObject> {
    // Crafty sends backups as a bare map ({"<backup id>": {...}}), without the usual
    // {"status": "ok", "data": ...} wrapper, so fall back to the whole reply.
    val root = this as? JsonObject ?: return emptyList()
    val d = if ("data" in root && "status" in root) root["data"] ?: return emptyList() else root
    fun fromArray(a: JsonArray): List<JsonObject> = a.mapNotNull { el ->
        when (el) {
            is JsonObject -> el
            is JsonPrimitive -> el.contentOrNull?.let { JsonObject(mapOf("backup_name" to JsonPrimitive(it))) }
            else -> null
        }
    }
    return when (d) {
        is JsonArray -> fromArray(d)
        is JsonObject -> {
            val nestedList = d.values.filterIsInstance<JsonArray>().firstOrNull()
            val nestedObjects = d.values.filterIsInstance<JsonObject>()
            when {
                nestedList != null -> fromArray(nestedList)
                nestedObjects.isNotEmpty() -> nestedObjects
                d.isNotEmpty() -> listOf(d)
                else -> emptyList()
            }
        }
        else -> emptyList()
    }
}

private fun String?.meaningful(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("false", true) && !it.equals("none", true) }

private val minecraftFormatting = Regex("§.")

data class ServerSummary(
    val id: String,
    val name: String,
    val type: String,
    /** Absolute folder on the Crafty host; the root for the file browser. */
    val path: String? = null,
    /** Server jar/executable file name, e.g. "paper-1.21.1.jar". */
    val executable: String? = null,
    /** The game port Crafty checks, e.g. 25566. */
    val port: Int? = null,
) {
    val isBedrock: Boolean get() = "bedrock" in type.lowercase()

    companion object {
        fun from(o: JsonObject): ServerSummary? {
            val id = o.str("server_id") ?: o.str("server_uuid") ?: return null
            return ServerSummary(
                id = id,
                name = o.str("server_name").meaningful() ?: str(R.string.unnamed_server),
                type = o.str("type").orEmpty(),
                path = o.str("path").meaningful(),
                executable = o.str("executable").meaningful(),
                port = o.int("server_port"),
            )
        }
    }
}

enum class ServerState(@StringRes private val labelRes: Int) {
    RUNNING(R.string.state_running),
    STARTING(R.string.state_starting),
    UPDATING(R.string.state_updating),
    STOPPED(R.string.state_stopped),
    CRASHED(R.string.state_crashed);

    val label: String get() = str(labelRes)
}

data class ServerStats(
    val running: Boolean,
    /** False when Crafty's reply had no "running" field, so [running] is only a guess. */
    val runningReported: Boolean = true,
    /** True while Crafty is still downloading or importing a new server's files. */
    val importing: Boolean = false,
    val crashed: Boolean,
    val waitingStart: Boolean,
    val updating: Boolean,
    val cpu: Double?,
    val mem: String?,
    val memPercent: Double?,
    val online: Int?,
    val max: Int?,
    val players: List<String>,
    val version: String?,
    val worldName: String?,
    val worldSize: String?,
    val started: String?,
    val port: Int?,
    val description: String?,
    /** Crafty's own ping of the game server; false means players/version are unknown, not zero. */
    val pingOk: Boolean? = null,
) {
    /** True when the player count is real data rather than a failed ping's default 0. */
    val playersKnown: Boolean
        get() = pingOk == true || (max ?: 0) > 0 || players.isNotEmpty()

    val state: ServerState
        get() = when {
            crashed -> ServerState.CRASHED
            updating -> ServerState.UPDATING
            waitingStart -> ServerState.STARTING
            running -> ServerState.RUNNING
            else -> ServerState.STOPPED
        }

    companion object {
        private val quoted = Regex("""'([^']*)'|"([^"]*)"""")

        fun from(root: JsonElement): ServerStats {
            val r = root as? JsonObject ?: JsonObject(emptyMap())
            // The stats normally live under "data"; older docs show them at the top level.
            val o = JsonObject(r + (r.obj("data") ?: emptyMap()))
            return ServerStats(
                running = o.bool("running") ?: false,
                runningReported = o.bool("running") != null,
                crashed = o.bool("crashed") ?: false,
                importing = o.bool("importing") ?: false,
                waitingStart = o.bool("waiting_start") ?: false,
                updating = o.bool("updating") ?: false,
                cpu = o.num("cpu"),
                // Newer Crafty sends raw bytes ("2129448960.0"); older sends "2.0GB". prettySize handles both.
                mem = o.str("mem").meaningful()?.let(::prettySize),
                memPercent = o.num("mem_percent"),
                online = o.int("online"),
                max = o.int("max"),
                players = parsePlayers(o["players"]),
                version = o.str("version").meaningful(),
                worldName = o.str("world_name").meaningful(),
                worldSize = o.str("world_size").meaningful(),
                started = o.str("started").meaningful(),
                port = o.int("server_port"),
                description = o.str("desc").meaningful()?.replace(minecraftFormatting, ""),
                pingOk = o.bool("int_ping_results"),
            )
        }

        private fun parsePlayers(e: JsonElement?): List<String> = when (e) {
            is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> e.contentOrNull.orEmpty().let { raw ->
                quoted.findAll(raw).map { m -> m.groupValues[1].ifEmpty { m.groupValues[2] } }
                    .filter { it.isNotBlank() }.toList()
            }
            else -> emptyList()
        }
    }
}

data class BackupConfig(
    val id: String?,
    val name: String,
    val isDefault: Boolean,
    val details: List<Pair<String, String>>,
    // Raw values for the editor.
    val maxBackups: Int? = null,
    val compress: Boolean = false,
    val shutdown: Boolean = false,
    val before: String = "",
    val after: String = "",
    val enabled: Boolean = true,
    /** Result of the last run as Crafty 4.4+ keeps it per profile, e.g. "Standby", "Failed". */
    val status: String? = null,
    val statusMessage: String? = null,
) {
    val failed: Boolean get() = status?.contains("fail", ignoreCase = true) == true

    companion object {
        /** Crafty stores status as a JSON string ('{"status": "Failed", "message": "..."}') or an object. */
        private fun statusOf(o: JsonObject): Pair<String?, String?> {
            val el = o["status"] ?: return null to null
            val obj = el as? JsonObject
                ?: (el as? JsonPrimitive)?.contentOrNull?.let { raw ->
                    runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
                        ?: return raw.meaningful() to null
                }
                ?: return null to null
            return obj.str("status").meaningful() to obj.str("message").meaningful()
        }

        private val shownFields: List<Pair<String, String>>
            get() = listOf(
                "backup_type" to str(R.string.backup_field_type),
                "enabled" to str(R.string.backup_field_enabled),
                "backup_location" to str(R.string.backup_field_location),
                "max_backups" to str(R.string.backup_field_keep),
                "compress" to str(R.string.backup_field_compressed),
                "shutdown" to str(R.string.backup_field_stops_server),
                "before" to str(R.string.backup_field_command_before),
                "after" to str(R.string.backup_field_command_after),
            )

        fun from(o: JsonObject): BackupConfig = BackupConfig(
            id = o.str("backup_id").meaningful(),
            name = o.str("backup_name").meaningful() ?: str(R.string.task_backup),
            isDefault = o.bool("default") ?: false,
            details = shownFields.mapNotNull { (key, label) ->
                val value = o.str(key)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val pretty = when (value.lowercase()) {
                    "true" -> str(R.string.yes)
                    "false" -> str(R.string.no)
                    else -> value
                }
                label to pretty
            },
            maxBackups = o.int("max_backups"),
            compress = o.bool("compress") ?: false,
            shutdown = o.bool("shutdown") ?: false,
            before = o.str("before").orEmpty(),
            after = o.str("after").orEmpty(),
            enabled = o.bool("enabled") ?: true,
            status = statusOf(o).first,
            statusMessage = statusOf(o).second,
        )
    }
}

/** The kinds of task Crafty can schedule, with the value its API uses. */
enum class TaskAction(val api: String, @StringRes private val labelRes: Int) {
    COMMAND("command", R.string.task_command),
    BACKUP("backup", R.string.task_backup),
    RESTART("restart", R.string.task_restart),
    START("start", R.string.task_start),
    STOP("stop", R.string.task_stop);

    val label: String get() = str(labelRes)

    companion object {
        /** Accepts both the short names and the *_server / custom_command spellings. */
        fun of(raw: String): TaskAction? = when (raw.lowercase()) {
            "command", "custom_command" -> COMMAND
            "backup", "backup_server" -> BACKUP
            "restart", "restart_server" -> RESTART
            "start", "start_server" -> START
            "stop", "stop_server" -> STOP
            else -> null
        }
    }
}

data class ScheduledTask(
    val id: String,
    val name: String,
    /** Raw Crafty action, e.g. "backup". */
    val action: String,
    /** For backup tasks: which backup config it runs; null means the default one. */
    val actionId: String?,
    val actionLabel: String,
    val command: String?,
    val schedule: String,
    val enabled: Boolean,
    // Raw schedule fields, kept so the editor can start from the current values.
    val interval: Int? = null,
    val intervalType: String? = null,
    val startTime: String? = null,
    val cronString: String? = null,
    val oneTime: Boolean = false,
    val delay: Int = 0,
) {
    val kind: TaskAction? get() = TaskAction.of(action)

    companion object {
        fun from(o: JsonObject): ScheduledTask? {
            val id = o.str("schedule_id") ?: o.str("id") ?: return null
            val action = o.str("action").orEmpty()
            return ScheduledTask(
                id = id,
                name = o.str("name").meaningful() ?: humanAction(action),
                action = action,
                actionId = o.str("action_id").meaningful(),
                actionLabel = humanAction(action),
                command = o.str("command").meaningful(),
                schedule = describe(o),
                enabled = o.bool("enabled") ?: false,
                interval = o.int("interval"),
                intervalType = o.str("interval_type").meaningful(),
                startTime = o.str("start_time").meaningful(),
                cronString = o.str("cron_string").meaningful(),
                oneTime = o.bool("one_time") ?: false,
                delay = o.int("delay") ?: 0,
            )
        }

        private fun humanAction(action: String): String =
            TaskAction.of(action)?.label
                ?: if (action.isEmpty()) str(R.string.task) else action.replace('_', ' ').replaceFirstChar { it.uppercase() }

        /** "Every 5 hours", worded for the units Crafty uses; other units are shown as Crafty names them. */
        private fun everyLabel(interval: String, type: String): String {
            val n = interval.toIntOrNull()
            val id = when (type) {
                "seconds" -> R.plurals.every_seconds
                "minutes" -> R.plurals.every_minutes
                "hours" -> R.plurals.every_hours
                "days" -> R.plurals.every_days
                "weeks" -> R.plurals.every_weeks
                else -> null
            }
            return if (n != null && id != null) plural(id, n, n) else str(R.string.every_x_x, interval, type)
        }

        private fun describe(o: JsonObject): String {
            val cron = o.str("cron_string").meaningful()
            if (cron != null) return str(R.string.cron_x, cron)
            val type = o.str("interval_type").meaningful()
            if (type == "reaction") return str(R.string.runs_after_another_task)
            val interval = o.str("interval").meaningful()
            val at = o.str("start_time").meaningful()
            val base = when {
                interval != null && type != null -> everyLabel(interval, type)
                type != null -> type.replaceFirstChar { it.uppercase() }
                else -> str(R.string.custom_schedule)
            }
            val withTime = if (at != null) str(R.string.x_at_x, base, at) else base
            return if (o.bool("one_time") == true) str(R.string.once_x, withTime) else withTime
        }
    }
}
