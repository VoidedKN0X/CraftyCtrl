package com.vknxworks.craftyremote.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Parsers for the endpoints that the official API docs don't describe in detail.
// Every one of them reads several plausible shapes and ignores what it doesn't recognise.

/** Parses Crafty's timestamps ("2026-10-13 12:00:00", ISO, or epoch seconds) to epoch millis. */
internal fun parseTimestamp(raw: String?): Long? {
    val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    s.toDoubleOrNull()?.let { n -> return if (n > 1e11) n.toLong() else (n * 1000).toLong() }
    runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
    runCatching { return Instant.parse(s).toEpochMilli() }
    val local = s.replace('T', ' ').substringBefore('.').substringBefore('+')
    for (pattern in listOf("yyyy-MM-dd HH:mm:ss", "MM/dd/yyyy, HH:mm:ss", "yyyy/MM/dd HH:mm:ss")) {
        runCatching {
            return LocalDateTime.parse(local, DateTimeFormatter.ofPattern(pattern))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
    return null
}

// ---------------------------------------------------------------- stats history

data class HistoryPoint(
    val time: Long,
    val cpu: Double?,
    val memPercent: Double?,
    val players: Int?,
)

internal fun parseHistory(root: JsonElement): List<HistoryPoint> {
    val data = (root as? JsonObject)?.get("data") ?: root
    val rows: List<JsonObject> = when (data) {
        is JsonArray -> data.filterIsInstance<JsonObject>()
        is JsonObject -> {
            // Either a single list under some key, or a map keyed by id/time.
            val list = data.values.filterIsInstance<JsonArray>().firstOrNull()
            list?.filterIsInstance<JsonObject>() ?: data.values.filterIsInstance<JsonObject>()
        }
        else -> emptyList()
    }
    return rows.mapNotNull { o ->
        val t = parseTimestamp(o.str("created") ?: o.str("time") ?: o.str("timestamp") ?: o.str("date"))
            ?: return@mapNotNull null
        HistoryPoint(
            time = t,
            cpu = o.num("cpu") ?: o.num("cpu_usage"),
            memPercent = o.num("mem_percent") ?: o.num("mem_usage_percent"),
            players = o.int("online") ?: o.int("players_online"),
        )
    }.sortedBy { it.time }
}

/** Keeps at most [max] points, evenly spaced, so long histories still draw quickly. */
internal fun List<HistoryPoint>.downsample(max: Int): List<HistoryPoint> {
    if (size <= max) return this
    val step = size.toDouble() / max
    return List(max) { i -> this[(i * step).toInt()] }
}

// ---------------------------------------------------------------- host stats

data class DiskUsage(
    val mount: String,
    val percent: Double?,
    val used: String?,
    val total: String?,
)

data class HostStats(
    val cpuPercent: Double?,
    val cpuCores: Int?,
    val cpuFreq: String?,
    val memPercent: Double?,
    val memUsed: String?,
    val memTotal: String?,
    val disks: List<DiskUsage>,
    val bootTime: String?,
) {
    val isEmpty: Boolean get() = cpuPercent == null && memPercent == null && disks.isEmpty()

    companion object {
        fun fromJson(root: JsonElement): HostStats {
            val r = root as? JsonObject ?: JsonObject(emptyMap())
            val o = JsonObject(r + (r.obj("data") ?: emptyMap()))
            return HostStats(
                cpuPercent = o.num("cpu_usage") ?: o.num("cpu_percent") ?: o.num("cpu"),
                cpuCores = o.int("cpu_cores") ?: o.int("cpu_count"),
                cpuFreq = o.str("cpu_cur_freq")?.let(::prettyFreq),
                memPercent = o.num("mem_percent"),
                memUsed = o.str("mem_usage")?.let(::prettySize),
                memTotal = o.str("mem_total")?.let(::prettySize),
                disks = parseDisks(o["disk_json"] ?: o["disk_data"] ?: o["disks"]),
                bootTime = o.str("boot_time"),
            )
        }

        /** Prometheus/OpenMetrics text from /metrics/host, e.g. "CPU_Usage 1.54". */
        fun fromMetrics(text: String): HostStats {
            val values = text.lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    val v = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                    parts[0].substringBefore('{') to v
                }.toMap()
            return HostStats(
                cpuPercent = values["CPU_Usage"],
                cpuCores = null, cpuFreq = null,
                memPercent = values["Mem_Usage"],
                memUsed = null, memTotal = null,
                disks = emptyList(), bootTime = null,
            )
        }

        private fun parseDisks(e: JsonElement?): List<DiskUsage> {
            val list: List<JsonObject> = when (e) {
                is JsonArray -> e.filterIsInstance<JsonObject>()
                is JsonObject -> e.values.filterIsInstance<JsonObject>()
                // Some versions send the disk list as a JSON string.
                is JsonPrimitive -> e.contentOrNull?.let { s ->
                    runCatching {
                        kotlinx.serialization.json.Json.parseToJsonElement(s)
                    }.getOrNull()?.let { parsed -> return parseDisks(parsed) }
                    emptyList()
                } ?: emptyList()
                else -> emptyList()
            }
            return list.map { d ->
                DiskUsage(
                    mount = d.str("mount") ?: d.str("device") ?: "Disk",
                    percent = d.num("percent"),
                    used = d.str("used")?.let(::prettySize),
                    total = d.str("total")?.let(::prettySize),
                )
            }.filterNot { it.mount.startsWith("/snap") || it.mount.startsWith("/boot") }
        }
    }
}

/** Turns raw byte counts into "12.3 GB"; leaves already-formatted strings alone. */
internal fun prettySize(raw: String): String {
    val n = raw.trim().toDoubleOrNull() ?: return raw
    if (n < 1024) return "${n.toLong()} B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = n / 1024
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return "%.1f %s".format(v, units[i])
}

private fun prettyFreq(raw: String): String {
    val n = raw.trim().toDoubleOrNull() ?: return raw
    return if (n >= 1000) "%.2f GHz".format(n / 1000) else "%.0f MHz".format(n)
}

// ---------------------------------------------------------------- files

data class FileEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
)

/** Result of asking Crafty for a path: a folder listing or a file's text. */
sealed interface FileResult {
    data class Folder(val entries: List<FileEntry>) : FileResult
    data class Text(val contents: String) : FileResult
}

internal fun parseFileResult(root: JsonElement, requestedPath: String): FileResult {
    val data = (root as? JsonObject)?.get("data")
    return when (data) {
        is JsonPrimitive -> FileResult.Text(data.contentOrNull.orEmpty())
        is JsonArray -> FileResult.Folder(data.mapNotNull { el ->
            when (el) {
                is JsonObject -> entryFrom(el, el.str("name"), requestedPath)
                is JsonPrimitive -> el.contentOrNull?.let { n ->
                    FileEntry(n.trimEnd('/'), join(requestedPath, n.trimEnd('/')), n.endsWith("/"))
                }
                else -> null
            }
        }.sorted())
        is JsonObject -> {
            // A single object with text content, or a map of name -> entry.
            data.str("contents")?.let { return FileResult.Text(it) }
            data.str("content")?.let { return FileResult.Text(it) }
            FileResult.Folder(data.entries.mapNotNull { (key, value) ->
                val o = value as? JsonObject ?: return@mapNotNull null
                if (key == "root_path" || o.bool("top") == true) return@mapNotNull null
                entryFrom(o, key, requestedPath)
            }.sorted())
        }
        else -> FileResult.Folder(emptyList())
    }
}

private fun entryFrom(o: JsonObject, key: String?, parent: String): FileEntry? {
    val path = o.str("path")?.takeIf { it.isNotBlank() }
    val name = key?.takeIf { it.isNotBlank() } ?: path?.trimEnd('/')?.substringAfterLast('/') ?: return null
    val isDir = o.bool("dir") ?: o.bool("is_dir") ?: o.bool("directory")
        ?: (o.str("type")?.lowercase()?.let { it == "dir" || it == "directory" || it == "folder" }) ?: false
    return FileEntry(name, path ?: join(parent, name), isDir)
}

private fun List<FileEntry>.sorted(): List<FileEntry> =
    sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })

internal fun join(parent: String, name: String): String =
    if (parent.isEmpty()) name else parent.trimEnd('/') + "/" + name

internal fun JsonElement.dataString(): String? = ((this as? JsonObject)?.get("data") as? JsonPrimitive)?.contentOrNull
