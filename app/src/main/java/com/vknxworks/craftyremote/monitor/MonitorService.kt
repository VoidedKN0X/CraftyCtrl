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

    private data class Snapshot(val running: Boolean, val crashed: Boolean, val players: Set<String>, val online: Int)

    private val last = mutableMapOf<String, Snapshot>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AppSettings(this).alertsEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannels(this)
        startInForeground(status("Starting…"))

        val settings = AppSettings(this)
        updateWakeLock(settings.alertKeepAwake)
        if (loop?.isActive != true) loop = scope.launch { run() }
        return START_STICKY
    }

    override fun onDestroy() {
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
                    notifyStatus(status("Signed out — open the app to sign in again"))
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

        var running = 0
        var playersOnline = 0
        for ((server, stats) in results) {
            if (stats == null) continue
            val now = snapshotOf(stats)
            if (now.running) running++
            playersOnline += now.online
            val before = last[server.id]
            last[server.id] = now
            if (before == null) continue // first sighting is the baseline, not an event

            if (settings.alertShutdown && before.running && !now.running) {
                val title = if (now.crashed) "${server.name} crashed" else "${server.name} stopped"
                val text = if (now.crashed) "Crafty detected a crash." else "The server is no longer running."
                alert(title, text, CHANNEL_SHUTDOWN)
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
        private const val STATUS_ID = 1001
        private const val CHANNEL_STATUS = "monitor_status"
        private const val CHANNEL_PLAYERS = "alerts_players"
        private const val CHANNEL_SHUTDOWN = "alerts_shutdown"
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
        }

        /** Starts or stops the service to match the saved settings. Call from the foreground. */
        fun sync(context: Context) {
            val settings = AppSettings(context)
            val signedIn = CredentialStore(context).load() != null
            val intent = Intent(context, MonitorService::class.java)
            if (settings.alertsEnabled && signedIn) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }

        /** Restarts the running service so it picks up changed settings (e.g. keep-awake). */
        fun restart(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
            sync(context)
        }
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
