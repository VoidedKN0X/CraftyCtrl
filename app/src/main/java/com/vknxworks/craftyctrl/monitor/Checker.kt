package com.vknxworks.craftyctrl.monitor

import android.content.Context
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.ServerStats
import com.vknxworks.craftyctrl.str
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** What one check found, for the monitor's ongoing notification. */
data class CheckSummary(
    val servers: Int,
    val running: Int,
    val playersOnline: Int,
    /** A server just looked stopped for the first time: check again soon to confirm it (restarts pass through one). */
    val confirmSoon: Boolean,
)

/**
 * One check of every server, shared by the foreground monitor, the background check and push wake-ups.
 * What it saw last time is saved, so a check started by a push hours later still compares with the one before.
 *
 * Alerts:
 * - a crash, straight away;
 * - a stop, once the server has looked stopped in two checks in a row (a restart passes through one);
 * - player joins and leaves on servers that have them switched on, from the online player list in Crafty's
 *   stats, with names taken from the console's "joined the game" / "left the game" lines when the list has none.
 */
object Checker {
    private const val PREFS = "alert_state"
    private const val KEY_DOWN_PREFIX = "down_alerted:"
    /** A stop or crash already notified (by push or a check) isn't notified again within this time. */
    private const val DOWN_DEDUPE_MS = 10 * 60_000L
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val joinLeave = Regex("""(?:\]:|:)\s*([A-Za-z0-9_.]{1,32}) (joined|left) the game""")

    private data class Snapshot(
        val running: Boolean,
        val crashed: Boolean,
        val online: Int,
        val players: List<String>,
        val playersKnown: Boolean,
        val stoppedPolls: Int = 0,
        val wasRunningBeforeStop: Boolean = false,
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Forgets what earlier checks saw (signing out, or alerts turned off), so the next check starts fresh. */
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /**
     * Claims the right to notify that [serverId] went down. False when that was already notified a moment ago,
     * so a push and a check (or two pushes) never both alert for the same stop.
     */
    fun claimDown(context: Context, serverId: String): Boolean {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val last = p.getLong(KEY_DOWN_PREFIX + serverId, 0L)
        if (now - last in 0 until DOWN_DEDUPE_MS) return false
        p.edit().putLong(KEY_DOWN_PREFIX + serverId, now).apply()
        return true
    }

    suspend fun check(context: Context, api: CraftyApi, settings: AppSettings): CheckSummary = mutex.withLock {
        coroutineScope {
            val servers = api.servers()
            val results = servers.map { s -> async { s to runCatching { api.stats(s.id) }.getOrNull() } }.awaitAll()
            val playerServers = settings.playerAlertServers
            val p = prefs(context)
            val edit = p.edit()
            var running = 0
            var playersOnline = 0
            var confirmSoon = false
            for ((server, stats) in results) {
                // Unknown or half-filled replies change nothing, so they can't cause false alerts.
                if (stats == null || !stats.runningReported) continue
                val before = p.getString(server.id, null)?.let(::decode)
                var now = snapshotOf(stats)
                if (before != null) {
                    val down = !now.running && !stats.waitingStart && !stats.updating
                    now = if (down) {
                        now.copy(
                            stoppedPolls = before.stoppedPolls + 1,
                            wasRunningBeforeStop = if (before.stoppedPolls == 0) before.running else before.wasRunningBeforeStop,
                        )
                    } else {
                        now.copy(stoppedPolls = 0)
                    }
                }
                if (now.running) running++
                playersOnline += now.online
                edit.putString(server.id, encode(now))
                if (before == null) continue // first sighting is the baseline, not an event

                when {
                    now.crashed && !before.crashed -> if (claimDown(context, server.id)) {
                        Alerts.server(context, str(R.string.x_crashed, server.name), str(R.string.crafty_detected_a_crash))
                    }
                    !now.crashed && now.stoppedPolls == 1 && now.wasRunningBeforeStop -> confirmSoon = true
                    !now.crashed && now.stoppedPolls == 2 && now.wasRunningBeforeStop -> if (claimDown(context, server.id)) {
                        Alerts.server(context, str(R.string.x_stopped, server.name), str(R.string.the_server_is_no_longer_running))
                    }
                }

                if (server.id in playerServers && before.running && now.running) {
                    playerChanges(context, api, server.id, server.name, before, now)
                }
            }
            edit.apply()
            CheckSummary(servers.size, running, playersOnline, confirmSoon)
        }
    }

    private suspend fun playerChanges(context: Context, api: CraftyApi, serverId: String, serverName: String, before: Snapshot, now: Snapshot) {
        // A failed ping reports 0 players: that's "unknown", not everyone leaving.
        if (!before.playersKnown || !now.playersKnown) return
        if (before.players.isNotEmpty() || now.players.isNotEmpty()) {
            val joined = now.players.filter { it !in before.players }
            val left = before.players.filter { it !in now.players }
            joined.forEach { Alerts.player(context, serverId, serverName, it, true, now.online) }
            left.forEach { Alerts.player(context, serverId, serverName, it, false, now.online) }
            // Crafty's list can be a sample of the names; a count change it doesn't explain is named from the console.
            val unexplained = (now.online - before.online) - (joined.size - left.size)
            if (unexplained != 0) fromConsole(context, api, serverId, serverName, unexplained, now.online, joined + left)
        } else if (now.online != before.online) {
            fromConsole(context, api, serverId, serverName, now.online - before.online, now.online, emptyList())
        }
    }

    /** Names [delta] joins (positive) or leaves (negative) from the newest console lines; "A player" when it can't. */
    private suspend fun fromConsole(context: Context, api: CraftyApi, serverId: String, serverName: String, delta: Int, online: Int, already: List<String>) {
        val joined = delta > 0
        val count = kotlin.math.abs(delta).coerceAtMost(5)
        val lines = runCatching { api.logs(serverId) }.getOrNull().orEmpty().takeLast(200)
        val names = lines.asReversed().mapNotNull { line ->
            joinLeave.find(line)?.takeIf { (it.groupValues[2] == "joined") == joined }?.groupValues?.get(1)
        }.filter { it !in already }.distinct().take(count)
        names.forEach { Alerts.player(context, serverId, serverName, it, joined, online) }
        repeat(count - names.size) {
            Alerts.player(context, serverId, serverName, str(R.string.a_player), joined, online)
        }
    }

    private fun snapshotOf(s: ServerStats) = Snapshot(
        running = s.running,
        crashed = s.crashed,
        online = s.online ?: s.players.size,
        players = s.players,
        playersKnown = s.playersKnown,
    )

    private fun encode(s: Snapshot): String = buildJsonObject {
        put("running", s.running)
        put("crashed", s.crashed)
        put("online", s.online)
        put("players", buildJsonArray { s.players.forEach { add(JsonPrimitive(it)) } })
        put("known", s.playersKnown)
        put("stopped", s.stoppedPolls)
        put("was", s.wasRunningBeforeStop)
    }.toString()

    private fun decode(raw: String): Snapshot? = runCatching {
        val o = json.parseToJsonElement(raw) as JsonObject
        fun b(k: String) = (o[k] as? JsonPrimitive)?.booleanOrNull ?: false
        fun i(k: String) = (o[k] as? JsonPrimitive)?.intOrNull ?: 0
        Snapshot(
            running = b("running"),
            crashed = b("crashed"),
            online = i("online"),
            players = (o["players"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
            playersKnown = b("known"),
            stoppedPolls = i("stopped"),
            wasRunningBeforeStop = b("was"),
        )
    }.getOrNull()
}
