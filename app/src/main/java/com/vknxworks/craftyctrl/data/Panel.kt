package com.vknxworks.craftyctrl.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Panel-wide things: Crafty's audit log, its users and its roles.

/** One line of Crafty's audit log (logs/audit.log: one JSON object per line, newest first from the API). */
data class AuditEntry(
    val time: String,
    val user: String,
    val message: String,
    val serverId: String?,
    val sourceIp: String?,
) {
    companion object {
        fun from(o: JsonObject): AuditEntry = AuditEntry(
            time = o.str("time").orEmpty(),
            user = o.str("user_name") ?: o.str("user_id").orEmpty(),
            message = o.str("log_msg").orEmpty(),
            serverId = o.str("server_id")?.takeIf { it.isNotBlank() && !it.equals("None", true) && !it.equals("null", true) },
            sourceIp = o.str("source_ip")?.takeIf { it.isNotBlank() && !it.equals("None", true) && !it.equals("null", true) },
        )
    }
}

/** A Crafty user as the users list and the single-user call give it. */
data class PanelUser(
    val id: String,
    val username: String,
    val enabled: Boolean,
    val superuser: Boolean,
    /** Role ids; only filled by the single-user call. */
    val roles: Set<String> = emptySet(),
) {
    companion object {
        fun from(o: JsonObject): PanelUser? {
            val id = o.str("user_id") ?: return null
            // The single-user call sends the roles as role objects; older versions as plain ids.
            val roles = (o["roles"] as? JsonArray).orEmpty().mapNotNull { r ->
                when (r) {
                    is JsonObject -> r.str("role_id")
                    is JsonPrimitive -> r.contentOrNull
                    else -> null
                }
            }.toSet()
            return PanelUser(
                id = id,
                username = o.str("username").orEmpty(),
                enabled = o.bool("enabled") ?: true,
                superuser = o.bool("superuser") ?: false,
                roles = roles,
            )
        }
    }
}

/** The three panel-wide permissions a user can have, in the order of Crafty's permission mask. */
enum class CraftyPermission { SERVER_CREATION, USER_CONFIG, ROLES_CONFIG }

/** A user's panel-wide permissions: which are on, and how many servers, users or roles they may make (-1 = no limit). */
data class UserPermissions(
    val enabled: Set<CraftyPermission>,
    val limits: Map<CraftyPermission, Int>,
) {
    companion object {
        fun from(root: JsonElement): UserPermissions {
            val o = (root as? JsonObject)?.obj("data") ?: JsonObject(emptyMap())
            val mask = o.str("permissions").orEmpty()
            val limits = o.obj("limits")
            return UserPermissions(
                enabled = CraftyPermission.entries.filter { mask.getOrNull(it.ordinal) == '1' }.toSet(),
                limits = CraftyPermission.entries.associateWith { limits?.int(it.name) ?: -1 },
            )
        }
    }
}

/** A Crafty role. */
data class PanelRole(
    val id: String,
    val name: String,
    val mfaRequired: Boolean,
) {
    companion object {
        fun from(o: JsonObject): PanelRole? {
            val id = o.str("role_id") ?: return null
            return PanelRole(id, o.str("role_name").orEmpty(), o.bool("mfa_required") ?: false)
        }
    }
}

/**
 * What a role may do on one server, in the order of Crafty's 8-character permission mask
 * ("11000000" = commands and terminal).
 */
enum class ServerPermission { COMMANDS, TERMINAL, LOGS, SCHEDULE, BACKUP, FILES, CONFIG, PLAYERS }

/** A role's access to one server. */
data class RoleServer(val serverId: String, val permissions: Set<ServerPermission>) {
    val mask: String get() = maskOf(permissions)

    companion object {
        fun maskOf(permissions: Set<ServerPermission>): String =
            ServerPermission.entries.joinToString("") { if (it in permissions) "1" else "0" }

        fun parseMask(mask: String): Set<ServerPermission> =
            ServerPermission.entries.filter { mask.getOrNull(it.ordinal) == '1' }.toSet()

        fun from(o: JsonObject): RoleServer? {
            val id = o.str("server_id") ?: return null
            return RoleServer(id, parseMask(o.str("permissions").orEmpty()))
        }
    }
}
