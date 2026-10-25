package com.vknxworks.craftyctrl.monitor

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vknxworks.craftyctrl.MainActivity
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.str
import java.util.concurrent.atomic.AtomicInteger

/** The notifications server alerts post, shared by the foreground monitor and the background check. */
object Alerts {
    const val CHANNEL_STATUS = "monitor_status"
    const val CHANNEL_SHUTDOWN = "alerts_shutdown"
    const val CHANNEL_PLAYERS = "players_join_leave"
    private const val GROUP_PLAYERS = "com.vknxworks.craftyctrl.PLAYERS"
    private const val PLAYERS_SUMMARY_ID = 1500
    private const val PAUSED_ID = 1501
    private val nextId = AtomicInteger(2000)

    /** The latest join and leave lines, newest first, for the grouped summary. */
    private val recentPlayerLines = ArrayDeque<String>()

    /** CraftyCtrl may post notifications: the Android 13+ permission is granted and they aren't turned off. */
    fun canNotify(context: Context): Boolean =
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, str(R.string.channel_alerts_service), NotificationManager.IMPORTANCE_MIN).apply {
                description = str(R.string.channel_alerts_service_desc)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SHUTDOWN, str(R.string.channel_shutdown), NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PLAYERS, str(R.string.channel_players), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = str(R.string.channel_players_desc)
            },
        )
        // Channels of the old player-join and backup alerts (removed in 2.x); this version uses new ones.
        nm.deleteNotificationChannel("alerts_players")
        nm.deleteNotificationChannel("alerts_backup")
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * What a secure lock screen shows when the phone hides sensitive notification content:
     * no server names, player names or panel errors (those can name the panel's address).
     */
    fun publicVersion(context: Context, channel: String, title: String): Notification =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(str(R.string.unlock_to_see_details))
            .build()

    /** A stop or crash alert. */
    @SuppressLint("MissingPermission")
    fun server(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_SHUTDOWN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context, CHANNEL_SHUTDOWN, str(R.string.a_server_needs_attention)))
            .build()
        NotificationManagerCompat.from(context).notify(nextId.incrementAndGet(), n)
    }

    /** "Alerts paused": always the same notification, so repeated checks don't stack it. */
    @SuppressLint("MissingPermission")
    fun paused(context: Context) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_SHUTDOWN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(str(R.string.alerts_paused))
            .setContentText(str(R.string.alerts_paused_body))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(PAUSED_ID, n)
    }

    /**
     * A player joined or left. One notification per player and server (a leave replaces that player's join),
     * all in one group with a summary, so a busy evening doesn't fill the shade.
     */
    @SuppressLint("MissingPermission")
    fun player(context: Context, serverId: String, serverName: String, player: String, joined: Boolean, online: Int?) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val title = if (joined) str(R.string.x_joined_x, player, serverName) else str(R.string.x_left_x, player, serverName)
        val text = online?.let { com.vknxworks.craftyctrl.plural(R.plurals.players_online, it, it) } ?: serverName
        val nm = NotificationManagerCompat.from(context)
        val n = NotificationCompat.Builder(context, CHANNEL_PLAYERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setGroup(GROUP_PLAYERS)
            .setContentIntent(openAppIntent(context))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context, CHANNEL_PLAYERS, str(R.string.player_activity)))
            .build()
        nm.notify(("player:$serverId:$player").hashCode(), n)

        val lines = synchronized(recentPlayerLines) {
            recentPlayerLines.addFirst(title)
            while (recentPlayerLines.size > 6) recentPlayerLines.removeLast()
            recentPlayerLines.toList()
        }
        val style = NotificationCompat.InboxStyle().setSummaryText(str(R.string.player_activity))
        lines.forEach { style.addLine(it) }
        val summary = NotificationCompat.Builder(context, CHANNEL_PLAYERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(str(R.string.player_activity))
            .setContentText(lines.first())
            .setStyle(style)
            .setGroup(GROUP_PLAYERS)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(context))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context, CHANNEL_PLAYERS, str(R.string.player_activity)))
            .build()
        nm.notify(PLAYERS_SUMMARY_ID, summary)
    }
}
