package com.vknxworks.craftyremote.data

/**
 * server.properties: every known setting for Java and Bedrock, plus a line-preserving editor.
 * Keys found in the file but not listed here still show up (under "Other"), so nothing is hidden.
 */

enum class PropKind { BOOL, INT, TEXT, SECRET, CHOICE }

data class PropDef(
    val key: String,
    val label: String,
    val group: String,
    val kind: PropKind,
    val default: String,
    val help: String? = null,
    val choices: List<String> = emptyList(),
    val min: Long? = null,
    val max: Long? = null,
)

private fun b(key: String, label: String, group: String, default: Boolean, help: String? = null) =
    PropDef(key, label, group, PropKind.BOOL, default.toString(), help)

private fun i(key: String, label: String, group: String, default: Long, min: Long? = null, max: Long? = null, help: String? = null) =
    PropDef(key, label, group, PropKind.INT, default.toString(), help, min = min, max = max)

private fun t(key: String, label: String, group: String, default: String = "", help: String? = null) =
    PropDef(key, label, group, PropKind.TEXT, default, help)

private fun s(key: String, label: String, group: String, help: String? = null) =
    PropDef(key, label, group, PropKind.SECRET, "", help)

private fun c(key: String, label: String, group: String, default: String, choices: List<String>, help: String? = null) =
    PropDef(key, label, group, PropKind.CHOICE, default, help, choices)

const val G_GAMEPLAY = "Gameplay"
const val G_PLAYERS = "Players & access"
const val G_WORLD = "World"
const val G_PERFORMANCE = "Performance"
const val G_NETWORK = "Network"
const val G_PACKS = "Resource packs"
const val G_RCON = "RCON, query & management"
const val G_ADVANCED = "Advanced"
const val G_OTHER = "Other"

/** Vanilla Java Edition server.properties keys (including ones newer or older versions use). */
val JAVA_PROPERTIES: List<PropDef> = listOf(
    // Gameplay
    c("gamemode", "Default game mode", G_GAMEPLAY, "survival", listOf("survival", "creative", "adventure", "spectator")),
    b("force-gamemode", "Force game mode on join", G_GAMEPLAY, false, "Players are put back in the default game mode whenever they join."),
    c("difficulty", "Difficulty", G_GAMEPLAY, "easy", listOf("peaceful", "easy", "normal", "hard")),
    b("hardcore", "Hardcore", G_GAMEPLAY, false, "Players are banned when they die."),
    b("pvp", "PvP", G_GAMEPLAY, true, "Older versions; newer ones use the pvp game rule."),
    b("allow-flight", "Allow flight", G_GAMEPLAY, false, "Stops survival players with fly mods from being kicked."),
    b("allow-nether", "Allow the Nether", G_GAMEPLAY, true),
    b("enable-command-block", "Command blocks", G_GAMEPLAY, false),
    b("spawn-monsters", "Spawn monsters", G_GAMEPLAY, true, "Older versions only."),
    b("spawn-animals", "Spawn animals", G_GAMEPLAY, true, "Older versions only."),
    b("spawn-npcs", "Spawn villagers", G_GAMEPLAY, true, "Older versions only."),
    i("spawn-protection", "Spawn protection radius", G_GAMEPLAY, 16, 0, help = "Blocks around spawn only operators can change. 0 turns it off."),
    // Players & access
    i("max-players", "Max players", G_PLAYERS, 20, 0, 2147483647),
    t("motd", "Message of the day", G_PLAYERS, "A Minecraft Server", "Shown in the multiplayer server list."),
    b("white-list", "Whitelist", G_PLAYERS, false, "Only whitelisted players can join."),
    b("enforce-whitelist", "Enforce whitelist", G_PLAYERS, false, "Kick players who aren't whitelisted when the whitelist is reloaded."),
    b("online-mode", "Online mode", G_PLAYERS, true, "Check accounts with Mojang. Turning this off allows cracked clients and is unsafe."),
    b("enforce-secure-profile", "Require signed chat", G_PLAYERS, true),
    b("prevent-proxy-connections", "Block VPN/proxy connections", G_PLAYERS, false),
    b("hide-online-players", "Hide online players", G_PLAYERS, false, "Don't show the player list in server pings."),
    i("player-idle-timeout", "Idle kick (minutes)", G_PLAYERS, 0, 0, help = "0 never kicks idle players."),
    i("op-permission-level", "Operator permission level", G_PLAYERS, 4, 1, 4),
    i("function-permission-level", "Function permission level", G_PLAYERS, 2, 1, 4),
    b("broadcast-console-to-ops", "Show console commands to ops", G_PLAYERS, true),
    b("log-ips", "Log player IPs", G_PLAYERS, true),
    b("accepts-transfers", "Accept transfers", G_PLAYERS, false, "Allow players to be transferred here from another server."),
    t("bug-report-link", "Bug report link", G_PLAYERS),
    // World
    t("level-name", "World folder name", G_WORLD, "world"),
    t("level-seed", "World seed", G_WORLD, "", "Only used when a new world is generated."),
    c("level-type", "World type", G_WORLD, "minecraft:normal",
        listOf("minecraft:normal", "minecraft:flat", "minecraft:large_biomes", "minecraft:amplified", "minecraft:single_biome_surface")),
    t("generator-settings", "Generator settings (JSON)", G_WORLD, "{}"),
    b("generate-structures", "Generate structures", G_WORLD, true),
    i("max-world-size", "Max world radius", G_WORLD, 29999984, 1, 29999984),
    t("initial-enabled-packs", "Initially enabled data packs", G_WORLD, "vanilla"),
    t("initial-disabled-packs", "Initially disabled data packs", G_WORLD),
    c("region-file-compression", "Region file compression", G_WORLD, "deflate", listOf("deflate", "lz4", "none")),
    // Performance
    i("view-distance", "View distance (chunks)", G_PERFORMANCE, 10, 3, 32),
    i("simulation-distance", "Simulation distance (chunks)", G_PERFORMANCE, 10, 3, 32),
    i("entity-broadcast-range-percentage", "Entity visibility range (%)", G_PERFORMANCE, 100, 10, 1000),
    i("max-tick-time", "Watchdog max tick time (ms)", G_PERFORMANCE, 60000, -1, help = "-1 disables the watchdog."),
    i("max-chained-neighbor-updates", "Max chained neighbour updates", G_PERFORMANCE, 1000000),
    i("pause-when-empty-seconds", "Pause when empty (seconds)", G_PERFORMANCE, 60, help = "0 or less never pauses."),
    b("sync-chunk-writes", "Synchronous chunk writes", G_PERFORMANCE, true),
    b("use-native-transport", "Native transport (Linux)", G_PERFORMANCE, true),
    // Network
    i("server-port", "Server port", G_NETWORK, 25565, 1, 65535, "Keep this the same as the port in Crafty's server config."),
    t("server-ip", "Bind address", G_NETWORK, "", "Leave empty to listen on all addresses."),
    i("network-compression-threshold", "Network compression threshold", G_NETWORK, 256, -1),
    i("rate-limit", "Packet rate limit", G_NETWORK, 0, 0, help = "0 disables it."),
    b("enable-status", "Show in server list", G_NETWORK, true),
    i("status-heartbeat-interval", "Status heartbeat interval", G_NETWORK, 0, 0),
    // Resource packs
    t("resource-pack", "Resource pack URL", G_PACKS),
    t("resource-pack-sha1", "Resource pack SHA-1", G_PACKS),
    t("resource-pack-id", "Resource pack UUID", G_PACKS),
    t("resource-pack-prompt", "Resource pack prompt", G_PACKS),
    b("require-resource-pack", "Require resource pack", G_PACKS, false),
    // RCON, query & management
    b("enable-rcon", "RCON", G_RCON, false),
    i("rcon.port", "RCON port", G_RCON, 25575, 1, 65535),
    s("rcon.password", "RCON password", G_RCON),
    b("broadcast-rcon-to-ops", "Show RCON commands to ops", G_RCON, true),
    b("enable-query", "Query (GameSpy4)", G_RCON, false),
    i("query.port", "Query port", G_RCON, 25565, 1, 65535),
    b("management-server-enabled", "Management server", G_RCON, false),
    t("management-server-host", "Management server host", G_RCON, "localhost"),
    i("management-server-port", "Management server port", G_RCON, 0, 0, 65535),
    s("management-server-secret", "Management server secret", G_RCON),
    b("management-server-tls-enabled", "Management server TLS", G_RCON, true),
    t("management-server-tls-keystore", "TLS keystore path", G_RCON),
    s("management-server-tls-keystore-password", "TLS keystore password", G_RCON),
    // Advanced
    b("enable-jmx-monitoring", "JMX monitoring", G_ADVANCED, false),
    t("text-filtering-config", "Text filtering config", G_ADVANCED),
    i("text-filtering-version", "Text filtering version", G_ADVANCED, 0, 0),
)

/** Bedrock Dedicated Server server.properties keys. */
val BEDROCK_PROPERTIES: List<PropDef> = listOf(
    // Gameplay
    c("gamemode", "Default game mode", G_GAMEPLAY, "survival", listOf("survival", "creative", "adventure")),
    b("force-gamemode", "Force game mode", G_GAMEPLAY, false),
    c("difficulty", "Difficulty", G_GAMEPLAY, "easy", listOf("peaceful", "easy", "normal", "hard")),
    b("allow-cheats", "Allow cheats", G_GAMEPLAY, false),
    c("default-player-permission-level", "New player permission", G_GAMEPLAY, "member", listOf("visitor", "member", "operator")),
    b("disable-player-interaction", "Disable player interaction", G_GAMEPLAY, false),
    // Players & access
    t("server-name", "Server name", G_PLAYERS, "Dedicated Server"),
    i("max-players", "Max players", G_PLAYERS, 10, 1),
    b("online-mode", "Require Xbox sign-in", G_PLAYERS, true),
    b("allow-list", "Allowlist", G_PLAYERS, false, "Only players on the allowlist can join."),
    i("player-idle-timeout", "Idle kick (minutes)", G_PLAYERS, 30, 0),
    c("chat-restriction", "Chat restriction", G_PLAYERS, "None", listOf("None", "Dropped", "Disabled")),
    b("disable-persona", "Disable persona skins", G_PLAYERS, false),
    b("disable-custom-skins", "Disable custom skins", G_PLAYERS, false),
    b("texturepack-required", "Require the world's resource packs", G_PACKS, false),
    // World
    t("level-name", "World name", G_WORLD, "Bedrock level"),
    t("level-seed", "World seed", G_WORLD),
    // Performance
    i("view-distance", "View distance (chunks)", G_PERFORMANCE, 32, 5),
    i("tick-distance", "Tick distance (chunks)", G_PERFORMANCE, 4, 4, 12),
    i("max-threads", "Max threads", G_PERFORMANCE, 8, 0, help = "0 uses as many as possible."),
    b("client-side-chunk-generation-enabled", "Client-side chunk generation", G_PERFORMANCE, true),
    PropDef("server-build-radius-ratio", "Server build radius ratio", G_PERFORMANCE, PropKind.TEXT, "Disabled"),
    // Network
    i("server-port", "IPv4 port", G_NETWORK, 19132, 1, 65535, "Keep this the same as the port in Crafty's server config."),
    i("server-portv6", "IPv6 port", G_NETWORK, 19133, 1, 65535),
    b("enable-lan-visibility", "Visible on LAN", G_NETWORK, true),
    i("compression-threshold", "Compression threshold", G_NETWORK, 1, 0, 65535),
    c("compression-algorithm", "Compression algorithm", G_NETWORK, "zlib", listOf("zlib", "snappy")),
    b("block-network-ids-are-hashes", "Hashed block network IDs", G_NETWORK, true),
    // Anti-cheat / movement
    b("server-authoritative-block-breaking", "Server-checked block breaking", G_ADVANCED, false),
    PropDef("server-authoritative-block-breaking-pick-range-scalar", "Block breaking range scalar", G_ADVANCED, PropKind.TEXT, "1.5"),
    b("correct-player-movement", "Correct player movement", G_ADVANCED, false),
    i("player-movement-score-threshold", "Movement score threshold", G_ADVANCED, 20, 0),
    PropDef("player-movement-action-direction-threshold", "Movement direction threshold", G_ADVANCED, PropKind.TEXT, "0.85"),
    PropDef("player-movement-distance-threshold", "Movement distance threshold", G_ADVANCED, PropKind.TEXT, "0.3"),
    i("player-movement-duration-threshold-in-ms", "Movement duration threshold (ms)", G_ADVANCED, 500, 0),
    // Logging / scripting / telemetry
    b("content-log-file-enabled", "Content log file", G_ADVANCED, false),
    c("content-log-level", "Content log level", G_ADVANCED, "info", listOf("verbose", "info", "warning", "error")),
    b("content-log-console-output-enabled", "Content log in console", G_ADVANCED, false),
    b("emit-server-telemetry", "Server telemetry", G_ADVANCED, false),
    b("allow-outbound-script-debugging", "Outbound script debugging", G_ADVANCED, false),
    b("allow-inbound-script-debugging", "Inbound script debugging", G_ADVANCED, false),
    b("script-debugger-auto-attach", "Script debugger auto-attach", G_ADVANCED, false),
)

val PROPERTY_GROUP_ORDER = listOf(G_GAMEPLAY, G_PLAYERS, G_WORLD, G_PERFORMANCE, G_NETWORK, G_PACKS, G_RCON, G_ADVANCED, G_OTHER)

/**
 * A parsed server.properties that remembers its exact lines (comments, order, blank lines),
 * so saving only touches the values that changed and appends keys that weren't there.
 */
class PropertiesFile(text: String) {
    private val lines: MutableList<String> = text.replace("\r\n", "\n").split('\n').toMutableList()

    val values: Map<String, String>
        get() = lines.mapNotNull(::parse).toMap()

    private fun parse(line: String): Pair<String, String>? {
        val l = line.trimStart()
        if (l.isEmpty() || l.startsWith("#") || l.startsWith("!")) return null
        val sep = l.indexOfFirst { it == '=' || it == ':' }.takeIf { it > 0 } ?: return null
        return l.substring(0, sep).trim() to l.substring(sep + 1).trim()
    }

    /** Returns the file text with [changes] applied (key -> new raw value). */
    fun render(changes: Map<String, String>): String {
        val pending = changes.toMutableMap()
        val out = lines.map { line ->
            val kv = parse(line)
            if (kv != null && kv.first in pending) "${kv.first}=${pending.remove(kv.first)}" else line
        }.toMutableList()
        // Drop the trailing empty line from split() before appending, then restore a final newline.
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.lastIndex)
        pending.forEach { (k, v) -> out += "$k=$v" }
        return out.joinToString("\n") + "\n"
    }
}

/** Java writes ':', '=', '#', '!' and non-ASCII escaped (minecraft\:normal, \u00e9). Show them plainly. */
fun unescapeProp(v: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < v.length) {
        val ch = v[i]
        if (ch == '\\' && i + 1 < v.length) {
            val n = v[i + 1]
            if (n == 'u' && i + 5 < v.length) {
                val code = v.substring(i + 2, i + 6).toIntOrNull(16)
                if (code != null) { sb.append(code.toChar()); i += 6; continue }
            }
            sb.append(when (n) { 't' -> '\t'; 'n' -> '\n'; 'r' -> '\r'; 'f' -> '\u000c'; else -> n })
            i += 2
        } else {
            sb.append(ch); i++
        }
    }
    return sb.toString()
}

/** Escapes a value the way Java's Properties.store does (Minecraft reads it back the same way). */
fun escapeProp(v: String): String = buildString {
    v.forEachIndexed { idx, ch ->
        when {
            ch == '\\' -> append("\\\\")
            ch == ':' || ch == '=' || ch == '#' || ch == '!' -> append('\\').append(ch)
            ch == ' ' && idx == 0 -> append("\\ ")
            ch == '\n' -> append("\\n")
            ch.code < 0x20 || ch.code > 0x7e -> append("\\u%04x".format(ch.code))
            else -> append(ch)
        }
    }
}
