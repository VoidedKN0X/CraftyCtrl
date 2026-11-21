package com.vknxworks.craftyctrl.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * CraftyCtrl's own settings as a small JSON file, to move them to another phone or keep a copy.
 *
 * Only settings: how often screens refresh, how often alerts check, which servers have player alerts and
 * the two "hide" switches. Never in the file: the Crafty login (token or API key), panel addresses, the app
 * PIN, anything about the app lock, the push address and its secret, confirmed certificates and the list of
 * panels that may use plain HTTP.
 *
 * Reading a file never turns anything off that protects you: it can switch "Hide in screenshots" and "Hide
 * in recent apps" on but not off, it never touches the app lock, never turns alerts or push on or off, never
 * trusts a certificate and never allows plain HTTP.
 */
data class SettingsBackup(
    val liveRefreshSeconds: Int?,
    val alertIntervalSeconds: Int?,
    val hideScreenshots: Boolean?,
    val hideRecents: Boolean?,
    val playerAlertServers: Set<String>?,
) {
    /** One setting an import would change: its key (for the label), what it is now and what it would become. */
    data class Change(val key: String, val from: String, val to: String)

    fun toJson(): String = buildJsonObject {
        put("app", APP)
        put("kind", KIND)
        put("version", VERSION)
        put(
            "settings",
            buildJsonObject {
                liveRefreshSeconds?.let { put(K_LIVE, it) }
                alertIntervalSeconds?.let { put(K_INTERVAL, it) }
                hideScreenshots?.let { put(K_SCREENSHOTS, it) }
                hideRecents?.let { put(K_RECENTS, it) }
                playerAlertServers?.let { set -> put(K_PLAYERS, buildJsonArray { set.sorted().forEach { add(JsonPrimitive(it)) } }) }
            },
        )
    }.toString()

    /** What applying this file on top of [current] would change. Empty when everything is already the same. */
    fun changesFrom(current: SettingsBackup): List<Change> = buildList {
        if (liveRefreshSeconds != null && liveRefreshSeconds != current.liveRefreshSeconds) {
            add(Change(K_LIVE, current.liveRefreshSeconds.toString(), liveRefreshSeconds.toString()))
        }
        if (alertIntervalSeconds != null && alertIntervalSeconds != current.alertIntervalSeconds) {
            add(Change(K_INTERVAL, current.alertIntervalSeconds.toString(), alertIntervalSeconds.toString()))
        }
        // The hide switches can only be switched on by a file.
        if (hideScreenshots == true && current.hideScreenshots != true) add(Change(K_SCREENSHOTS, "false", "true"))
        if (hideRecents == true && current.hideRecents != true) add(Change(K_RECENTS, "false", "true"))
        if (playerAlertServers != null && playerAlertServers != current.playerAlertServers.orEmpty()) {
            add(Change(K_PLAYERS, current.playerAlertServers.orEmpty().size.toString(), playerAlertServers.size.toString()))
        }
    }

    companion object {
        const val APP = "CraftyCtrl"
        const val KIND = "settings"
        const val VERSION = 1
        const val K_LIVE = "live_refresh_seconds"
        const val K_INTERVAL = "alert_interval_seconds"
        const val K_SCREENSHOTS = "hide_screenshots"
        const val K_RECENTS = "hide_recents"
        const val K_PLAYERS = "player_alert_servers"

        /** The values the settings screen offers; anything else in a file is ignored. */
        val LIVE_CHOICES = setOf(1, 2, 5, 10)
        val INTERVAL_CHOICES = setOf(15, 30, 60, 300)
        private val serverId = Regex("^[A-Za-z0-9-]{1,64}$")
        /** A settings file is tiny; anything bigger isn't one. */
        const val MAX_BYTES = 256 * 1024

        /** Reads a settings file. Null when it isn't a CraftyCtrl settings file; unknown or odd values are skipped. */
        fun parse(text: String): SettingsBackup? {
            val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
            if ((root["app"] as? JsonPrimitive)?.contentOrNull != APP) return null
            if ((root["kind"] as? JsonPrimitive)?.contentOrNull != KIND) return null
            val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: return null
            if (version < 1 || version > VERSION) return null
            val s = root["settings"] as? JsonObject ?: return null
            fun int(key: String) = (s[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            fun bool(key: String) = (s[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
            val players = (s[K_PLAYERS] as? JsonArray)?.mapNotNull { e ->
                (e as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { serverId.matches(it) }
            }?.take(500)?.toSet()
            return SettingsBackup(
                liveRefreshSeconds = int(K_LIVE)?.takeIf { it in LIVE_CHOICES },
                alertIntervalSeconds = int(K_INTERVAL)?.takeIf { it in INTERVAL_CHOICES },
                hideScreenshots = bool(K_SCREENSHOTS),
                hideRecents = bool(K_RECENTS),
                playerAlertServers = players,
            )
        }

        /** The settings as they are now. */
        fun current(context: Context): SettingsBackup {
            val settings = AppSettings(context)
            return SettingsBackup(
                liveRefreshSeconds = settings.liveRefreshSeconds,
                alertIntervalSeconds = settings.alertIntervalSeconds,
                hideScreenshots = settings.hideScreenshots,
                hideRecents = settings.hideRecents,
                playerAlertServers = settings.playerAlertServers,
            )
        }

        /** Applies exactly the changes [SettingsBackup.changesFrom] listed. */
        fun apply(context: Context, incoming: SettingsBackup) {
            val settings = AppSettings(context)
            val now = current(context)
            for (change in incoming.changesFrom(now)) {
                when (change.key) {
                    K_LIVE -> incoming.liveRefreshSeconds?.let { settings.liveRefreshSeconds = it }
                    K_INTERVAL -> incoming.alertIntervalSeconds?.let { settings.alertIntervalSeconds = it }
                    K_SCREENSHOTS -> settings.hideScreenshots = true
                    K_RECENTS -> settings.hideRecents = true
                    K_PLAYERS -> incoming.playerAlertServers?.let { settings.playerAlertServers = it }
                }
            }
        }
    }
}
