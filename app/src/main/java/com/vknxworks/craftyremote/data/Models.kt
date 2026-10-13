package com.vknxworks.craftyremote.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
) {
    val isBedrock: Boolean get() = "bedrock" in type.lowercase()

    companion object {
        fun from(o: JsonObject): ServerSummary? {
            val id = o.str("server_id") ?: o.str("server_uuid") ?: return null
            return ServerSummary(
                id = id,
                name = o.str("server_name").meaningful() ?: "Unnamed server",
                type = o.str("type").orEmpty(),
                path = o.str("path").meaningful(),
            )
        }
    }
}

enum class ServerState(val label: String) {
    RUNNING("Running"),
    STARTING("Starting"),
    UPDATING("Updating"),
    STOPPED("Stopped"),
    CRASHED("Crashed"),
}

data class ServerStats(
    val running: Boolean,
    /** False when Crafty's reply had no "running" field, so [running] is only a guess. */
    val runningReported: Boolean = true,
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
) {
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
                waitingStart = o.bool("waiting_start") ?: false,
                updating = o.bool("updating") ?: false,
                cpu = o.num("cpu"),
                mem = o.str("mem").meaningful(),
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
) {
    companion object {
        private val shownFields = listOf(
            "backup_type" to "Type",
            "enabled" to "Enabled",
            "backup_location" to "Location",
            "max_backups" to "Keep",
            "compress" to "Compressed",
            "shutdown" to "Stops server",
            "before" to "Command before",
            "after" to "Command after",
        )

        fun from(o: JsonObject): BackupConfig = BackupConfig(
            id = o.str("backup_id").meaningful(),
            name = o.str("backup_name").meaningful() ?: "Backup",
            isDefault = o.bool("default") ?: false,
            details = shownFields.mapNotNull { (key, label) ->
                val value = o.str(key)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val pretty = when (value.lowercase()) {
                    "true" -> "Yes"
                    "false" -> "No"
                    else -> value
                }
                label to pretty
            },
        )
    }
}

data class ScheduledTask(
    val id: String,
    val name: String,
    /** Raw Crafty action, e.g. "backup_server". */
    val action: String,
    /** For backup tasks: which backup config it runs; null means the default one. */
    val actionId: String?,
    val actionLabel: String,
    val command: String?,
    val schedule: String,
    val enabled: Boolean,
) {
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
            )
        }

        private fun humanAction(action: String): String = when (action) {
            "start_server" -> "Start server"
            "stop_server" -> "Stop server"
            "restart_server" -> "Restart server"
            "backup_server" -> "Backup"
            "custom_command", "command" -> "Console command"
            "" -> "Task"
            else -> action.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }

        private fun describe(o: JsonObject): String {
            val cron = o.str("cron_string").meaningful()
            if (cron != null) return "Cron · $cron"
            val type = o.str("interval_type").meaningful()
            if (type == "reaction") return "Runs after another task"
            val interval = o.str("interval").meaningful()
            val at = o.str("start_time").meaningful()
            val base = when {
                interval != null && type != null -> "Every $interval $type"
                type != null -> type.replaceFirstChar { it.uppercase() }
                else -> "Custom schedule"
            }
            val withTime = if (at != null) "$base at $at" else base
            return if (o.bool("one_time") == true) "Once · $withTime" else withTime
        }
    }
}
