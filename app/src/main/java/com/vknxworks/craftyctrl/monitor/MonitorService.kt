package com.vknxworks.craftyctrl.monitor

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.CraftyException
import com.vknxworks.craftyctrl.data.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.vknxworks.craftyctrl.str
import com.vknxworks.craftyctrl.plural

/**
 * The foreground monitor: checks Crafty every few seconds to minutes (Settings > Check every) and notifies when
 * a server stops or crashes, and when players join or leave servers that have player alerts on. Runs as a
 * foreground service (with a quiet ongoing notification) so Android keeps it alive. With push from Crafty
 * through ntfy and "Push only" chosen, this service doesn't run at all: [CheckJob] does the checks instead.
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Alerts.ensureChannels(this)
        // Must come first: Android kills the app if a foreground start isn't confirmed quickly.
        try {
            startInForeground(status(str(R.string.starting)))
        } catch (e: Exception) {
            // e.g. restarted by the system while the app is in the background (Android 12+).
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                AppSettings(this).alertsEnabled = false
                // As when alerts are turned off in Settings: their copy of the login and what they last saw go too.
                CredentialStore(this).syncBackgroundCopy(false)
                Checker.reset(this)
                CheckJob.schedule(this, false)
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
            val store = CredentialStore(this)
            val session = store.load()
            val token = store.backgroundToken()
            if (!settings.alertsEnabled || pushOnly(this) || session == null || token == null) {
                stopSelf()
                return
            }
            val api = CraftyApi(session.baseUrl, token)
            try {
                val s = Checker.check(this, api, settings)
                val summary = str(
                    R.string.monitor_summary,
                    plural(R.plurals.watching_servers, s.servers, s.servers),
                    str(R.string.x_running, s.running),
                    plural(R.plurals.players_online, s.playersOnline, s.playersOnline),
                )
                notifyStatus(status(summary))
            } catch (e: CancellationException) {
                throw e
            } catch (e: CraftyException) {
                if (e.httpCode == 401) {
                    Alerts.paused(this)
                    stopSelf()
                    return
                }
                notifyStatus(status(str(R.string.can_t_reach_crafty_x, e.message)))
            } catch (e: Exception) {
                notifyStatus(status(str(R.string.can_t_reach_crafty)))
            }
            delay(settings.alertIntervalSeconds.coerceAtLeast(10) * 1000L)
        }
    }

    // ------------------------------------------------------------ notifications

    private fun status(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // Turning alerts off from the lock screen needs the phone unlocked first.
        val turnOff = NotificationCompat.Action.Builder(0, str(R.string.turn_off), stop)
            .setAuthenticationRequired(true)
            .build()
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(str(R.string.server_alerts_on))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(Alerts.openAppIntent(this))
            .addAction(turnOff)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(Alerts.publicVersion(this, CHANNEL_STATUS, str(R.string.server_alerts_on)))
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

    @SuppressLint("WakelockTimeout")
    private fun updateWakeLock(keepAwake: Boolean) {
        if (keepAwake && wakeLock?.isHeld != true) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CraftyCtrl:monitor").apply {
                setReferenceCounted(false)
                acquire()
            }
        } else if (!keepAwake) {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }
    }

    companion object {
        private const val ACTION_STOP = "com.vknxworks.craftyctrl.STOP_MONITOR"
        private const val ACTION_QUIT = "com.vknxworks.craftyctrl.QUIT_MONITOR"

        @Volatile
        private var isRunning = false
        private const val STATUS_ID = 1001
        private const val CHANNEL_STATUS = Alerts.CHANNEL_STATUS

        fun canNotify(context: Context): Boolean = Alerts.canNotify(context)

        /** Push only is chosen and can work: without ntfy installed, the foreground monitor runs instead. */
        fun pushOnly(context: Context): Boolean =
            AppSettings(context).pushOnly && com.vknxworks.craftyctrl.push.UnifiedPush.isNtfyInstalled(context)

        fun ensureChannels(context: Context) = Alerts.ensureChannels(context)

        /**
         * Starts or stops alerts to match the saved settings: the foreground monitor, or (push only) the
         * 15-minute background check, and the ntfy registration. Call from the foreground.
         */
        fun sync(context: Context) {
            val settings = AppSettings(context)
            val signedIn = CredentialStore(context).load() != null
            val intent = Intent(context, MonitorService::class.java)
            // Without notifications the alerts can't reach anyone, so they wait until they're allowed
            // (the app syncs again when it comes back on screen).
            val on = settings.alertsEnabled && signedIn && canNotify(context)
            if (!on) Checker.reset(context)
            val pushOnly = pushOnly(context)
            CheckJob.schedule(context, on && pushOnly)
            if (on && !pushOnly) {
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
