package com.vknxworks.craftyctrl.monitor

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.CraftyException
import com.vknxworks.craftyctrl.data.CredentialStore
import com.vknxworks.craftyctrl.push.PushEvent
import com.vknxworks.craftyctrl.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The background check without the foreground monitor (Android's JobScheduler, no extra library):
 * - every 15 minutes while alerts run push-only (Android doesn't run periodic jobs more often), as the fallback
 *   for pushes that don't arrive;
 * - once, straight away, whenever a push from Crafty arrives (in either alert mode), with the event it carried;
 * - once, a minute later, when a check saw a server stop for the first time, to confirm it wasn't a restart.
 */
class CheckJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val extras = params.extras
        val event = extras.getString(KEY_EVENT)?.let { PushEvent(it, extras.getString(KEY_SERVER), extras.getString(KEY_DETAIL)) }
        stopped = false
        running = scope.launch {
            try {
                work(event)
            } finally {
                // After onStopJob Android has already ended the job (and reschedules it if asked): don't finish it again.
                if (!stopped) runCatching { jobFinished(params, false) }
            }
        }
        return true
    }

    @Volatile private var stopped = false

    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        running?.cancel()
        return params.jobId == PERIODIC_ID
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun work(event: PushEvent?) {
        val settings = AppSettings(this)
        val store = CredentialStore(this)
        val session = store.load() ?: return
        val token = store.backgroundToken() ?: return
        if (!settings.alertsEnabled || !Alerts.canNotify(this)) return
        val api = CraftyApi(session.baseUrl, token)
        try {
            if (event != null) handleEvent(api, event)
            val summary = Checker.check(this, api, settings)
            if (summary.confirmSoon) confirmLater(this)
        } catch (e: CraftyException) {
            if (e.httpCode == 401) Alerts.paused(this)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Can't reach Crafty: the next check or push tries again.
        }
    }

    /**
     * Acts on the event a push carried. Anyone who knows the push address can post to it, so the server must
     * exist on the panel and its state is checked with Crafty before anything is notified.
     */
    private suspend fun handleEvent(api: CraftyApi, event: PushEvent) {
        val id = event.serverId ?: return
        val server = api.servers().firstOrNull { it.id == id } ?: return
        when (event.event) {
            "crash_detected" -> {
                // Only when Crafty confirms it: crashed, or not running (anyone with the push address could send this).
                val stats = runCatching { api.stats(id) }.getOrNull() ?: return
                if (stats.runningReported && (stats.crashed || !stats.running) && Checker.claimDown(this, id)) {
                    Alerts.server(this, str(R.string.x_crashed, server.name), str(R.string.crafty_detected_a_crash))
                }
            }
            "stop_server", "kill" -> {
                // A restart sends a stop too: wait, and only alert when it stays down.
                delay(RESTART_GRACE_MS)
                val stats = runCatching { api.stats(id) }.getOrNull() ?: return
                if (stats.runningReported && !stats.running && !stats.waitingStart && !stats.updating &&
                    Checker.claimDown(this, id)
                ) {
                    val title = if (event.event == "kill") str(R.string.x_killed, server.name) else str(R.string.x_stopped, server.name)
                    Alerts.server(this, title, str(R.string.the_server_is_no_longer_running))
                }
            }
        }
    }

    companion object {
        private const val PERIODIC_ID = 4100
        private const val NOW_ID = 4101
        private const val CONFIRM_ID = 4102
        private const val KEY_EVENT = "event"
        private const val KEY_SERVER = "server"
        private const val KEY_DETAIL = "detail"
        private const val RESTART_GRACE_MS = 20_000L

        private fun scheduler(context: Context) = context.getSystemService(JobScheduler::class.java)
        private fun component(context: Context) = ComponentName(context, CheckJob::class.java)

        /** Runs the 15-minute check while [on], and cancels it otherwise. */
        fun schedule(context: Context, on: Boolean) {
            val js = scheduler(context) ?: return
            if (!on) {
                js.cancel(PERIODIC_ID)
                js.cancel(CONFIRM_ID)
                return
            }
            if (js.getPendingJob(PERIODIC_ID) != null) return
            val job = JobInfo.Builder(PERIODIC_ID, component(context))
                .setPeriodic(15 * 60_000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            runCatching { js.schedule(job) }
        }

        /** A check now, after a push. Expedited, so it runs at once even with the phone asleep. */
        fun runNow(context: Context, event: PushEvent?) {
            val extras = PersistableBundle().apply {
                event?.let {
                    putString(KEY_EVENT, it.event)
                    putString(KEY_SERVER, it.serverId)
                    putString(KEY_DETAIL, it.detail)
                }
            }
            val job = JobInfo.Builder(NOW_ID, component(context))
                .setExpedited(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras)
                .build()
            val js = scheduler(context) ?: return
            if (runCatching { js.schedule(job) }.getOrDefault(JobScheduler.RESULT_FAILURE) != JobScheduler.RESULT_SUCCESS) {
                // Out of expedited quota: run as a normal job instead.
                runCatching {
                    js.schedule(
                        JobInfo.Builder(NOW_ID, component(context))
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setExtras(extras)
                            .build(),
                    )
                }
            }
        }

        private fun confirmLater(context: Context) {
            val job = JobInfo.Builder(CONFIRM_ID, component(context))
                .setMinimumLatency(60_000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()
            runCatching { scheduler(context)?.schedule(job) }
        }
    }
}
