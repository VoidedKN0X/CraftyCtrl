package com.vknxworks.craftyremote.monitor

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vknxworks.craftyremote.MainActivity
import com.vknxworks.craftyremote.R
import com.vknxworks.craftyremote.data.AppSettings
import com.vknxworks.craftyremote.data.CraftyApi
import com.vknxworks.craftyremote.data.CraftyException
import com.vknxworks.craftyremote.data.CredentialStore
import com.vknxworks.craftyremote.data.BackupConfig
import com.vknxworks.craftyremote.data.ServerConfig
import com.vknxworks.craftyremote.data.ServerStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Polls Crafty in the background and notifies on player joins and server shutdowns.
 * Runs as a foreground service (with a quiet ongoing notification) so Android keeps it alive.
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private data class Snapshot(
        val running: Boolean,
        val crashed: Boolean,
        val players: Set<String>,
        val online: Int,
        /** Polls in a row that looked stopped; a restart passes through one, a real stop stays. */
        val stoppedPolls: Int = 0,
        /** Whether the server was running when the current stopped streak began. */
        val wasRunningBeforeStop: Boolean = false,
        /** Set when the player list suddenly came back empty; one such poll is treated as a ping blip. */
        val emptyBlip: Boolean = false,
    )

    private val last = mutableMapOf<String, Snapshot>()

    /** Last seen backup state per server: whether one was running, and when the last one finished. */
    private data class BackupSnap(val running: Boolean, val lastBackup: String?, val awaitingResult: Boolean = false)
    private val lastBackup = mutableMapOf<String, BackupSnap>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        // Must come first: Android kills the app if a foreground start isn't confirmed quickly.
        try {
            startInForeground(status("Starting…"))
        } catch (e: Exception) {
            // e.g. restarted by the system while the app is in the background (Android 12+).
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                AppSettings(this).alertsEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_QUIT -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val settings = AppSettings(this)
        updateWakeLock(settings.alertKeepAwake)
        if (loop?.isActive != true) loop = scope.launch { run() }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private suspend fun run() {
        while (scope.isActive) {
            val settings = AppSettings(this)
            val session = CredentialStore(this).load()
            if (!settings.alertsEnabled || session == null) {
                stopSelf()
                return
            }
            val api = CraftyApi(session.baseUrl, session.token)
            try {
                poll(api, settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CraftyException) {
                if (e.httpCode == 401) {
                    alert("Server alerts paused", "Crafty signed this phone out. Open the app to sign in again.", CHANNEL_SHUTDOWN)
                    stopSelf()
                    return
                }
                notifyStatus(status("Can't reach Crafty: ${e.message}"))
            } catch (e: Exception) {
                notifyStatus(status("Can't reach Crafty"))
            }
            delay(settings.alertIntervalSeconds.coerceAtLeast(10) * 1000L)
        }
    }

    private suspend fun poll(api: CraftyApi, settings: AppSettings) = coroutineScope {
        val servers = api.servers()
        val results = servers.map { s ->
            async { s to runCatching { api.stats(s.id) }.getOrNull() }
        }.awaitAll()
        if (settings.alertBackup) {
            val info = servers.map { s ->
                async {
                    Triple(s, runCatching { api.serverConfig(s.id) }.getOrNull(), runCatching { api.backups(s.id) }.getOrNull())
                }
            }.awaitAll()
            for ((server, cfg, profiles) in info) checkBackup(server.id, server.name, cfg, profiles)
        } else {
            lastBackup.clear()
            profileStatus.clear()
        }

        var running = 0
        var playersOnline = 0
        for ((server, stats) in results) {
            // Unknown or half-filled replies change nothing, so they can't cause false alerts.
            if (stats == null || !stats.runningReported) continue
            val before = last[server.id]
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
                // A running server whose list suddenly empties is usually a failed ping; wait one poll.
                if (now.running && now.players.isEmpty() && before.players.isNotEmpty() && !before.emptyBlip) {
                    now = now.copy(players = before.players, online = before.online, emptyBlip = true)
                }
            }
            if (now.running) running++
            playersOnline += now.online
            last[server.id] = now
            if (before == null) continue // first sighting is the baseline, not an event

            if (settings.alertShutdown) {
                when {
                    // Crashes are reported straight away.
                    now.crashed && !before.crashed ->
                        alert("${server.name} crashed", "Crafty detected a crash.", CHANNEL_SHUTDOWN)
                    // A plain stop must hold for two polls, so restarts don't alert.
                    !now.crashed && now.stoppedPolls == 2 && now.wasRunningBeforeStop ->
                        alert("${server.name} stopped", "The server is no longer running.", CHANNEL_SHUTDOWN)
                }
            }
            if (settings.alertPlayerJoin && now.running) {
                val joined = now.players - before.players
                when {
                    joined.isNotEmpty() -> {
                        val who = joined.sorted().joinToString(", ")
                        alert("$who joined ${server.name}", "${now.online} online now", CHANNEL_PLAYERS)
                    }
                    // Bedrock usually reports a count but no names.
                    before.players.isEmpty() && now.players.isEmpty() && now.online > before.online -> {
                        val n = now.online - before.online
                        val who = if (n == 1) "A player" else "$n players"
                        alert("$who joined ${server.name}", "${now.online} online now", CHANNEL_PLAYERS)
                    }
                }
            }
        }
        val summary = "Watching ${servers.size} server${if (servers.size == 1) "" else "s"} · " +
            "$running running · $playersOnline player${if (playersOnline == 1) "" else "s"} online"
        notifyStatus(status(summary))
    }

    /** Per backup profile (Crafty 4.4+): last status seen, e.g. "standby", "running", "failed". */
    private val profileStatus = mutableMapOf<String, String>()
    /** When each server last got a backup alert of each kind, so two signals for one backup alert once. */
    private val lastBackupAlert = mutableMapOf<String, Long>()

    private fun backupAlert(serverId: String, failed: Boolean, title: String, text: String) {
        val key = "$serverId/$failed"
        val now = System.currentTimeMillis()
        if (now - (lastBackupAlert[key] ?: 0L) < BACKUP_ALERT_DEDUP_MS) return
        lastBackupAlert[key] = now
        alert(title, text, CHANNEL_BACKUP)
    }

    /**
     * Backup results, from whichever signals this Crafty version gives:
     * - each backup profile's status turning "Failed", or going from running back to idle;
     * - the server's last-backup time moving on (finished);
     * - the server's backing-up flag dropping while that time stays put for another poll (failed).
     * Only results are notified, never the start.
     */
    private fun checkBackup(id: String, name: String, cfg: ServerConfig?, profiles: List<BackupConfig>?) {
        val hasProfileStatus = profiles?.any { it.status != null } == true
        val several = (profiles?.size ?: 0) > 1
        profiles?.forEach { b ->
            val status = b.status?.lowercase() ?: return@forEach
            val key = "$id/${b.id ?: b.name}"
            val before = profileStatus.put(key, status) ?: return@forEach // first sighting is the baseline
            if (before == status) return@forEach
            val label = if (several) "${b.name} on $name" else name
            when {
                b.failed -> backupAlert(id, true, "Backup failed: $label", b.statusMessage ?: "Crafty reported the backup as failed.")
                isActive(before) && !isActive(status) -> backupAlert(id, false, "Backup finished: $label", "The backup completed.")
            }
        }

        if (cfg == null) return
        var now = BackupSnap(cfg.backingUp == true, cfg.lastBackup)
        val before = lastBackup[id]
        lastBackup[id] = now
        if (before == null) return // first sighting is the baseline
        val finished = now.lastBackup != null && now.lastBackup != before.lastBackup
        when {
            finished -> backupAlert(id, false, "Backup finished: $name", "Completed ${now.lastBackup}")
            // Profiles with a status say directly when a backup fails; otherwise infer it.
            hasProfileStatus -> Unit
            before.awaitingResult && !now.running ->
                backupAlert(id, true, "Backup failed: $name", "The backup stopped without completing. Check the server's logs in Crafty.")
            before.running && !now.running -> {
                now = now.copy(awaitingResult = true)
                lastBackup[id] = now
            }
        }
    }

    private fun isActive(status: String) =
        status !in listOf("standby", "idle", "failed", "completed", "complete", "success", "done", "none", "")

    private fun snapshotOf(s: ServerStats) = Snapshot(
        running = s.running,
        crashed = s.crashed,
        players = s.players.toSet(),
        online = s.online ?: s.players.size,
    )

    // ------------------------------------------------------------ notifications

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun status(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Server alerts on")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Turn off", stop)
            .build()
    }

    private fun startInForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(STATUS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(STATUS_ID, n)
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyStatus(n: Notification) {
        if (canNotify(this)) NotificationManagerCompat.from(this).notify(STATUS_ID, n)
    }

    @SuppressLint("MissingPermission")
    private fun alert(title: String, text: String, channel: String) {
        if (!canNotify(this)) return
        val n = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        NotificationManagerCompat.from(this).notify(nextId.incrementAndGet(), n)
    }

    @SuppressLint("WakelockTimeout")
    private fun updateWakeLock(keepAwake: Boolean) {
        if (keepAwake && wakeLock?.isHeld != true) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CraftyRemote:monitor").apply {
                setReferenceCounted(false)
                acquire()
            }
        } else if (!keepAwake) {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }
    }

    companion object {
        private const val ACTION_STOP = "com.vknxworks.craftyremote.STOP_MONITOR"
        private const val ACTION_QUIT = "com.vknxworks.craftyremote.QUIT_MONITOR"

        @Volatile
        private var isRunning = false
        private const val STATUS_ID = 1001
        private const val CHANNEL_STATUS = "monitor_status"
        private const val CHANNEL_PLAYERS = "alerts_players"
        private const val CHANNEL_SHUTDOWN = "alerts_shutdown"
        private const val CHANNEL_BACKUP = "alerts_backup"
        private const val BACKUP_ALERT_DEDUP_MS = 3 * 60_000L
        private val nextId = AtomicInteger(2000)

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_STATUS, "Alerts service", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Quiet notification shown while server alerts are on"
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PLAYERS, "Player joins", NotificationManager.IMPORTANCE_DEFAULT),
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SHUTDOWN, "Server stopped or crashed", NotificationManager.IMPORTANCE_HIGH),
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_BACKUP, "Backup finished or failed", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        /** Starts or stops the service to match the saved settings. Call from the foreground. */
        fun sync(context: Context) {
            val settings = AppSettings(context)
            val signedIn = CredentialStore(context).load() != null
            val intent = Intent(context, MonitorService::class.java)
            if (settings.alertsEnabled && signedIn) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else if (isRunning) {
                // Stopping through onStartCommand avoids the crash when stopService beats startForeground.
                runCatching { ContextCompat.startForegroundService(context, intent.setAction(ACTION_QUIT)) }
            }
        }

        /** Makes a running service pick up changed settings (e.g. keep-awake). */
        fun restart(context: Context) = sync(context)
    }
}

/** Resumes alerts after the phone restarts or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            MonitorService.sync(context)
        }
    }
}
