package com.vknxworks.craftyctrl.monitor

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.data.ConsoleSocket
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The progress notification for a start, a stop or a backup you asked for in the app: it stays in the
 * notification shade while Crafty works on it and ends with how it went.
 *
 * It runs outside any one screen, so leaving the server page doesn't end it. It only lives as long as the
 * app's process: if Android closes the app, the notification removes itself after its time limit
 * (setTimeoutAfter), and nothing is left behind. While the app is locked there is no token, so the
 * notification waits; it picks up again when you unlock.
 */
object ActionProgress {
    enum class Kind { START, STOP, BACKUP }

    private const val CHANNEL = "action_progress"
    private const val POLL_MS = 2_000L
    /** How long a start or stop is followed. A big modpack can take minutes to start. */
    private const val POWER_LIMIT_MS = 5 * 60_000L
    /** How long a backup is followed. */
    private const val BACKUP_LIMIT_MS = 60 * 60_000L
    /** How long the last notification ("Started", "Backup finished") stays if you don't dismiss it. */
    private const val RESULT_STAYS_MS = 10 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = HashMap<String, Job>()

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, str(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW).apply {
                description = str(R.string.channel_progress_desc)
            },
        )
    }

    private fun notificationId(serverId: String, kind: Kind): Int = ("progress:$serverId:${kind.name}").hashCode()

    /**
     * Follows [kind] on the server until it's done, failed or the time limit passed. Does nothing when
     * CraftyCtrl may not post notifications. A second start/stop on the same server replaces the first.
     */
    fun watch(context: Context, api: CraftyApi, serverId: String, serverName: String, kind: Kind, backupId: String? = null) {
        val app = context.applicationContext
        if (!Alerts.canNotify(app)) return
        ensureChannel(app)
        val key = if (kind == Kind.BACKUP) "backup:$serverId" else "power:$serverId"
        jobs.remove(key)?.cancel()
        // A start replaces a stop's notification on the same server and the other way round.
        if (kind != Kind.BACKUP) {
            val other = if (kind == Kind.START) Kind.STOP else Kind.START
            NotificationManagerCompat.from(app).cancel(notificationId(serverId, other))
        }
        jobs[key] = scope.launch {
            try {
                if (kind == Kind.BACKUP) followBackup(app, api, serverId, serverName, backupId) else followPower(app, api, serverId, serverName, kind)
            } finally {
                if (jobs[key] === coroutineContext[Job]) jobs.remove(key)
            }
        }
    }

    private suspend fun followPower(context: Context, api: CraftyApi, serverId: String, name: String, kind: Kind) {
        val start = kind == Kind.START
        val working = str(if (start) R.string.progress_starting_x else R.string.progress_stopping_x, name)
        post(context, serverId, kind, working, null, ongoing = true, percent = null, limitMs = POWER_LIMIT_MS)
        val deadline = android.os.SystemClock.elapsedRealtime() + POWER_LIMIT_MS
        // Crafty needs a moment before its stats show the new state.
        delay(1_500)
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val stats = try {
                api.stats(serverId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Locked, or the panel didn't answer this time: keep waiting.
                null
            }
            if (stats != null) {
                if (stats.crashed) {
                    post(context, serverId, kind, str(R.string.progress_crashed_x, name), null, ongoing = false, percent = null, limitMs = RESULT_STAYS_MS)
                    return
                }
                val done = if (start) stats.running && !stats.waitingStart else !stats.running && !stats.waitingStart
                if (done) {
                    post(
                        context, serverId, kind,
                        str(if (start) R.string.progress_started_x else R.string.progress_stopped_x, name),
                        null, ongoing = false, percent = null, limitMs = RESULT_STAYS_MS,
                    )
                    return
                }
            }
            delay(POLL_MS)
        }
        post(
            context, serverId, kind,
            str(if (start) R.string.progress_still_starting_x else R.string.progress_still_stopping_x, name),
            str(R.string.progress_open_to_check), ongoing = false, percent = null, limitMs = RESULT_STAYS_MS,
        )
    }

    /**
     * Crafty reports a backup's percentage only over its WebSocket ("backup_status" on the server's page),
     * so the notification opens one for as long as the backup runs. When the socket can't connect or says
     * nothing, the bar keeps moving without a percentage and the end is read from the backup's status.
     */
    private suspend fun followBackup(context: Context, api: CraftyApi, serverId: String, name: String, backupId: String?) {
        val kind = Kind.BACKUP
        /** The profile this backup runs under: the given one, else the default one; null when Crafty lists neither. */
        suspend fun profile(): com.vknxworks.craftyctrl.data.BackupConfig? = try {
            val list = api.backups(serverId)
            list.firstOrNull { backupId != null && it.id == backupId } ?: list.firstOrNull { it.isDefault } ?: list.singleOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        // A profile whose last run failed stays "Failed" until a run succeeds, so remember how it started.
        val failedBefore = profile()?.failed == true
        val working = str(R.string.progress_backing_up_x, name)
        post(context, serverId, kind, working, null, ongoing = true, percent = null, limitMs = BACKUP_LIMIT_MS)
        var percent: Int? = null
        var finished = false
        var socket: ConsoleSocket? = null
        fun openSocket() {
            val token = api.currentToken() ?: return
            val s = ConsoleSocket(api.baseUrl, serverId, token, object : ConsoleSocket.Listener {
                override fun onOpen() {}
                override fun onLine(line: String) {}
                override fun onClosed(unauthorized: Boolean) {
                    scope.launch { if (socket != null) socket = null }
                }
                override fun onEvent(event: String, data: JsonElement) {
                    if (event != "backup_status") return
                    val p = ((data as? JsonObject)?.get("percent") as? JsonPrimitive)?.doubleOrNull ?: return
                    scope.launch {
                        percent = p.toInt().coerceIn(0, 100)
                        if (p >= 100.0) finished = true
                    }
                }
            })
            if (s.connect()) socket = s
        }
        try {
            val deadline = android.os.SystemClock.elapsedRealtime() + BACKUP_LIMIT_MS
            var shown: Int? = null
            var sawProgress = false
            var quietChecks = 0
            while (android.os.SystemClock.elapsedRealtime() < deadline && !finished) {
                if (socket == null) openSocket()
                delay(POLL_MS)
                val p = percent
                if (p != null && p != shown && !finished) {
                    shown = p
                    sawProgress = true
                    post(context, serverId, kind, working, str(R.string.progress_percent, p), ongoing = true, percent = p, limitMs = BACKUP_LIMIT_MS)
                }
                // No percentage from the socket: after a minute, and then every half minute, ask Crafty
                // whether the backup failed. (A finished backup can't be told apart from one that's still
                // running by its status alone, so without the socket this only ends on a failure or the limit.)
                if (!sawProgress) {
                    quietChecks++
                    if (quietChecks >= 30 && quietChecks % 15 == 0) {
                        if (!failedBefore && profile()?.failed == true) finished = true
                    }
                }
            }
            if (!finished) {
                post(context, serverId, kind, str(R.string.progress_backup_still_x, name), str(R.string.progress_open_to_check), ongoing = false, percent = null, limitMs = RESULT_STAYS_MS)
                return
            }
            // Crafty sends 100 % for a finished backup and for a failed one; the profile's status tells them apart.
            // Crafty writes that status right after the message, so give it a moment.
            delay(2_000)
            val failure = profile()?.takeIf { it.failed }
            if (failure != null) {
                post(context, serverId, kind, str(R.string.progress_backup_failed_x, name), failure.statusMessage, ongoing = false, percent = null, limitMs = RESULT_STAYS_MS)
            } else {
                post(context, serverId, kind, str(R.string.progress_backup_done_x, name), null, ongoing = false, percent = null, limitMs = RESULT_STAYS_MS)
            }
        } finally {
            val s = socket
            socket = null
            s?.close()
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(
        context: Context,
        serverId: String,
        kind: Kind,
        title: String,
        text: String?,
        ongoing: Boolean,
        percent: Int?,
        limitMs: Long,
    ) {
        if (!Alerts.canNotify(context)) return
        val public = str(
            when {
                !ongoing -> R.string.progress_public_done
                kind == Kind.BACKUP -> R.string.progress_public_backup
                else -> R.string.progress_public_power
            },
        )
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(Alerts.openAppIntent(context))
            .setCategory(if (ongoing) NotificationCompat.CATEGORY_PROGRESS else NotificationCompat.CATEGORY_STATUS)
            // Removes itself if the app is closed before the work is done, and some time after the result.
            .setTimeoutAfter(limitMs + 30_000L)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(public)
                    .setContentText(str(R.string.unlock_to_see_details))
                    .build(),
            )
        if (!text.isNullOrBlank()) b.setContentText(text)
        if (ongoing) {
            if (percent != null) b.setProgress(100, percent, false) else b.setProgress(0, 0, true)
        }
        NotificationManagerCompat.from(context).notify(notificationId(serverId, kind), b.build())
    }
}
