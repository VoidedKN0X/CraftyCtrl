package com.vknxworks.craftyctrl.data
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str

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

val G_GAMEPLAY: String get() = str(R.string.group_gameplay)
val G_PLAYERS: String get() = str(R.string.group_players)
val G_WORLD: String get() = str(R.string.group_world)
val G_PERFORMANCE: String get() = str(R.string.group_performance)
val G_NETWORK: String get() = str(R.string.group_network)
val G_PACKS: String get() = str(R.string.group_packs)
val G_RCON: String get() = str(R.string.group_rcon)
val G_ADVANCED: String get() = str(R.string.group_advanced)
val G_OTHER: String get() = str(R.string.group_other)

/** Vanilla Java Edition server.properties keys (including ones newer or older versions use). */
val JAVA_PROPERTIES: List<PropDef> get() = listOf(
    // Gameplay
    c("gamemode", str(R.string.default_game_mode), G_GAMEPLAY, "survival", listOf("survival", "creative", "adventure", "spectator")),
    b("force-gamemode", str(R.string.force_game_mode_on_join), G_GAMEPLAY, false, str(R.string.players_are_put_back_in_the)),
    c("difficulty", str(R.string.difficulty), G_GAMEPLAY, "easy", listOf("peaceful", "easy", "normal", "hard")),
    b("hardcore", str(R.string.hardcore), G_GAMEPLAY, false, str(R.string.players_are_banned_when_they_die)),
    b("pvp", "PvP", G_GAMEPLAY, true, str(R.string.older_versions_newer_ones_use_the)),
    b("allow-flight", str(R.string.allow_flight), G_GAMEPLAY, false, str(R.string.stops_survival_players_with_fly_mods)),
    b("allow-nether", str(R.string.allow_the_nether), G_GAMEPLAY, true),
    b("enable-command-block", str(R.string.command_blocks), G_GAMEPLAY, false),
    b("spawn-monsters", str(R.string.spawn_monsters), G_GAMEPLAY, true, str(R.string.older_versions_only)),
    b("spawn-animals", str(R.string.spawn_animals), G_GAMEPLAY, true, str(R.string.older_versions_only)),
    b("spawn-npcs", str(R.string.spawn_villagers), G_GAMEPLAY, true, str(R.string.older_versions_only)),
    i("spawn-protection", str(R.string.spawn_protection_radius), G_GAMEPLAY, 16, 0, help = str(R.string.blocks_around_spawn_only_operators_can)),
    // Players & access
    i("max-players", str(R.string.max_players), G_PLAYERS, 20, 0, 2147483647),
    t("motd", str(R.string.message_of_the_day), G_PLAYERS, "A Minecraft Server", str(R.string.shown_in_the_multiplayer_server_list)),
    b("white-list", str(R.string.list_whitelist), G_PLAYERS, false, str(R.string.only_whitelisted_players_can_join)),
    b("enforce-whitelist", str(R.string.enforce_whitelist), G_PLAYERS, false, str(R.string.kick_players_who_aren_t_whitelisted)),
    b("online-mode", str(R.string.online_mode), G_PLAYERS, true, str(R.string.check_accounts_with_mojang_turning_this)),
    b("enforce-secure-profile", str(R.string.require_signed_chat), G_PLAYERS, true),
    b("prevent-proxy-connections", str(R.string.block_vpn_proxy_connections), G_PLAYERS, false),
    b("hide-online-players", str(R.string.hide_online_players), G_PLAYERS, false, str(R.string.don_t_show_the_player_list)),
    i("player-idle-timeout", str(R.string.idle_kick_minutes), G_PLAYERS, 0, 0, help = str(R.string.s_0_never_kicks_idle_players)),
    i("op-permission-level", str(R.string.operator_permission_level), G_PLAYERS, 4, 0, 4),
    i("function-permission-level", str(R.string.function_permission_level), G_PLAYERS, 2, 0, 4),
    b("broadcast-console-to-ops", str(R.string.show_console_commands_to_ops), G_PLAYERS, true),
    b("log-ips", str(R.string.log_player_ips), G_PLAYERS, true),
    b("accepts-transfers", str(R.string.accept_transfers), G_PLAYERS, false, str(R.string.allow_players_to_be_transferred_here)),
    t("bug-report-link", str(R.string.bug_report_link), G_PLAYERS),
    // World
    t("level-name", str(R.string.world_folder_name), G_WORLD, "world"),
    t("level-seed", str(R.string.world_seed), G_WORLD, "", str(R.string.only_used_when_a_new_world)),
    c("level-type", str(R.string.world_type), G_WORLD, "minecraft:normal",
        listOf("minecraft:normal", "minecraft:flat", "minecraft:large_biomes", "minecraft:amplified", "minecraft:single_biome_surface")),
    t("generator-settings", str(R.string.generator_settings_json), G_WORLD, "{}"),
    b("generate-structures", str(R.string.generate_structures), G_WORLD, true),
    i("max-world-size", str(R.string.max_world_radius), G_WORLD, 29999984, 1, 29999984),
    t("initial-enabled-packs", str(R.string.initially_enabled_data_packs), G_WORLD, "vanilla"),
    t("initial-disabled-packs", str(R.string.initially_disabled_data_packs), G_WORLD),
    c("region-file-compression", str(R.string.region_file_compression), G_WORLD, "deflate", listOf("deflate", "lz4", "none")),
    // Performance
    i("view-distance", str(R.string.view_distance_chunks), G_PERFORMANCE, 10, 3, 32),
    i("simulation-distance", str(R.string.simulation_distance_chunks), G_PERFORMANCE, 10, 3, 32),
    i("entity-broadcast-range-percentage", str(R.string.entity_visibility_range), G_PERFORMANCE, 100, 10, 1000),
    i("max-tick-time", str(R.string.watchdog_max_tick_time_ms), G_PERFORMANCE, 60000, -1, help = str(R.string.s_1_disables_the_watchdog)),
    i("max-chained-neighbor-updates", str(R.string.max_chained_neighbour_updates), G_PERFORMANCE, 1000000),
    i("pause-when-empty-seconds", str(R.string.pause_when_empty_seconds), G_PERFORMANCE, 60, help = str(R.string.s_0_or_less_never_pauses)),
    b("sync-chunk-writes", str(R.string.synchronous_chunk_writes), G_PERFORMANCE, true),
    b("use-native-transport", str(R.string.native_transport_linux), G_PERFORMANCE, true),
    // Network
    i("server-port", str(R.string.server_port), G_NETWORK, 25565, 1, 65535, str(R.string.keep_this_the_same_as_the)),
    t("server-ip", str(R.string.bind_address), G_NETWORK, "", str(R.string.leave_empty_to_listen_on_all)),
    i("network-compression-threshold", str(R.string.network_compression_threshold), G_NETWORK, 256, -1),
    i("rate-limit", str(R.string.packet_rate_limit), G_NETWORK, 0, 0, help = str(R.string.s_0_disables_it)),
    b("enable-status", str(R.string.show_in_server_list), G_NETWORK, true),
    i("status-heartbeat-interval", str(R.string.status_heartbeat_interval), G_NETWORK, 0, 0),
    // Resource packs
    t("resource-pack", str(R.string.resource_pack_url), G_PACKS),
    t("resource-pack-sha1", str(R.string.resource_pack_sha_1), G_PACKS),
    t("resource-pack-id", str(R.string.resource_pack_uuid), G_PACKS),
    t("resource-pack-prompt", str(R.string.resource_pack_prompt), G_PACKS),
    b("require-resource-pack", str(R.string.require_resource_pack), G_PACKS, false),
    // RCON, query & management
    b("enable-rcon", "RCON", G_RCON, false),
    i("rcon.port", str(R.string.rcon_port), G_RCON, 25575, 1, 65535),
    s("rcon.password", str(R.string.rcon_password), G_RCON),
    b("broadcast-rcon-to-ops", str(R.string.show_rcon_commands_to_ops), G_RCON, true),
    b("enable-query", str(R.string.query_gamespy4), G_RCON, false),
    i("query.port", str(R.string.query_port), G_RCON, 25565, 1, 65535),
    b("management-server-enabled", str(R.string.management_server), G_RCON, false),
    t("management-server-host", str(R.string.management_server_host), G_RCON, "localhost"),
    i("management-server-port", str(R.string.management_server_port), G_RCON, 0, 0, 65535),
    s("management-server-secret", str(R.string.management_server_secret), G_RCON),
    b("management-server-tls-enabled", str(R.string.management_server_tls), G_RCON, true),
    t("management-server-tls-keystore", str(R.string.tls_keystore_path), G_RCON),
    s("management-server-tls-keystore-password", str(R.string.tls_keystore_password), G_RCON),
    // Advanced
    b("enable-jmx-monitoring", str(R.string.jmx_monitoring), G_ADVANCED, false),
    t("text-filtering-config", str(R.string.text_filtering_config), G_ADVANCED),
    i("text-filtering-version", str(R.string.text_filtering_version), G_ADVANCED, 0, 0),
)

/** Bedrock Dedicated Server server.properties keys. */
val BEDROCK_PROPERTIES: List<PropDef> get() = listOf(
    // Gameplay
    c("gamemode", str(R.string.default_game_mode), G_GAMEPLAY, "survival", listOf("survival", "creative", "adventure")),
    b("force-gamemode", str(R.string.force_game_mode), G_GAMEPLAY, false),
    c("difficulty", str(R.string.difficulty), G_GAMEPLAY, "easy", listOf("peaceful", "easy", "normal", "hard")),
    b("allow-cheats", str(R.string.allow_cheats), G_GAMEPLAY, false),
    c("default-player-permission-level", str(R.string.new_player_permission), G_GAMEPLAY, "member", listOf("visitor", "member", "operator")),
    b("disable-player-interaction", str(R.string.disable_player_interaction), G_GAMEPLAY, false),
    // Players & access
    t("server-name", str(R.string.server_name), G_PLAYERS, "Dedicated Server"),
    i("max-players", str(R.string.max_players), G_PLAYERS, 10, 1),
    b("online-mode", str(R.string.require_xbox_sign_in), G_PLAYERS, true),
    b("allow-list", str(R.string.list_allowlist), G_PLAYERS, false, str(R.string.only_players_on_the_allowlist_can)),
    i("player-idle-timeout", str(R.string.idle_kick_minutes), G_PLAYERS, 30, 0),
    c("chat-restriction", str(R.string.chat_restriction), G_PLAYERS, "None", listOf("None", "Dropped", "Disabled")),
    b("disable-persona", str(R.string.disable_persona_skins), G_PLAYERS, false),
    b("disable-custom-skins", str(R.string.disable_custom_skins), G_PLAYERS, false),
    b("texturepack-required", str(R.string.require_the_world_s_resource_packs), G_PACKS, false),
    // World
    t("level-name", str(R.string.world_name), G_WORLD, "Bedrock level"),
    t("level-seed", str(R.string.world_seed), G_WORLD),
    // Performance
    i("view-distance", str(R.string.view_distance_chunks), G_PERFORMANCE, 32, 5),
    i("tick-distance", str(R.string.tick_distance_chunks), G_PERFORMANCE, 4, 4, 12),
    i("max-threads", str(R.string.max_threads), G_PERFORMANCE, 8, 0, help = str(R.string.s_0_uses_as_many_as_possible)),
    b("client-side-chunk-generation-enabled", str(R.string.client_side_chunk_generation), G_PERFORMANCE, true),
    PropDef("server-build-radius-ratio", str(R.string.server_build_radius_ratio), G_PERFORMANCE, PropKind.TEXT, "Disabled"),
    // Network
    i("server-port", str(R.string.ipv4_port), G_NETWORK, 19132, 1, 65535, str(R.string.keep_this_the_same_as_the)),
    i("server-portv6", str(R.string.ipv6_port), G_NETWORK, 19133, 1, 65535),
    b("enable-lan-visibility", str(R.string.visible_on_lan), G_NETWORK, true),
    i("compression-threshold", str(R.string.compression_threshold), G_NETWORK, 1, 0, 65535),
    c("compression-algorithm", str(R.string.compression_algorithm), G_NETWORK, "zlib", listOf("zlib", "snappy")),
    b("block-network-ids-are-hashes", str(R.string.hashed_block_network_ids), G_NETWORK, true),
    // Anti-cheat / movement
    b("server-authoritative-block-breaking", str(R.string.server_checked_block_breaking), G_ADVANCED, false),
    PropDef("server-authoritative-block-breaking-pick-range-scalar", str(R.string.block_breaking_range_scalar), G_ADVANCED, PropKind.TEXT, "1.5"),
    b("correct-player-movement", str(R.string.correct_player_movement), G_ADVANCED, false),
    i("player-movement-score-threshold", str(R.string.movement_score_threshold), G_ADVANCED, 20, 0),
    PropDef("player-movement-action-direction-threshold", str(R.string.movement_direction_threshold), G_ADVANCED, PropKind.TEXT, "0.85"),
    PropDef("player-movement-distance-threshold", str(R.string.movement_distance_threshold), G_ADVANCED, PropKind.TEXT, "0.3"),
    i("player-movement-duration-threshold-in-ms", str(R.string.movement_duration_threshold_ms), G_ADVANCED, 500, 0),
    // Logging / scripting / telemetry
    b("content-log-file-enabled", str(R.string.content_log_file), G_ADVANCED, false),
    c("content-log-level", str(R.string.content_log_level), G_ADVANCED, "info", listOf("verbose", "info", "warning", "error")),
    b("content-log-console-output-enabled", str(R.string.content_log_in_console), G_ADVANCED, false),
    b("emit-server-telemetry", str(R.string.server_telemetry), G_ADVANCED, false),
    b("allow-outbound-script-debugging", str(R.string.outbound_script_debugging), G_ADVANCED, false),
    b("allow-inbound-script-debugging", str(R.string.inbound_script_debugging), G_ADVANCED, false),
    b("script-debugger-auto-attach", str(R.string.script_debugger_auto_attach), G_ADVANCED, false),
)

val PROPERTY_GROUP_ORDER: List<String> get() = listOf(G_GAMEPLAY, G_PLAYERS, G_WORLD, G_PERFORMANCE, G_NETWORK, G_PACKS, G_RCON, G_ADVANCED, G_OTHER)

/**
 * A parsed server.properties that remembers its exact lines (comments, order, blank lines),
 * so saving only touches the values that changed and appends keys that weren't there.
 */
class PropertiesFile(text: String) {
    private val bom = text.startsWith('\uFEFF')
    private val newline = if ("\r\n" in text) "\r\n" else "\n"
    private val lines: MutableList<String> = text.removePrefix("\uFEFF").replace("\r\n", "\n").split('\n').toMutableList()

    /** Later lines win for duplicate keys, the same as Java's Properties. */
    val values: Map<String, String>
        get() = lines.mapNotNull(::parse).toMap()

    private fun parse(line: String): Pair<String, String>? {
        val l = line.trimStart()
        if (l.isEmpty() || l.startsWith("#") || l.startsWith("!")) return null
        val sep = l.indexOfFirst { it == '=' || it == ':' }.takeIf { it > 0 } ?: return null
        // Like Java: whitespace after the separator is dropped, trailing whitespace is part of the value.
        return l.substring(0, sep).trim() to l.substring(sep + 1).trimStart()
    }

    /** Returns the file text with [changes] applied (key -> new raw value), in its original line endings. */
    fun render(changes: Map<String, String>): String {
        val seen = mutableSetOf<String>()
        val out = lines.map { line ->
            val kv = parse(line)
            if (kv != null && kv.first in changes) {
                seen += kv.first
                "${kv.first}=${changes.getValue(kv.first)}" // every copy of a duplicated key gets the new value
            } else {
                line
            }
        }.toMutableList()
        // Drop the trailing empty line from split() before appending, then restore a final newline.
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.lastIndex)
        changes.filterKeys { it !in seen }.forEach { (k, v) -> out += "$k=$v" }
        return (if (bom) "\uFEFF" else "") + out.joinToString(newline) + newline
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
