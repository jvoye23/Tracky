package com.jvcs.tracky.core.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jvcs.tracky.composeapp.R
import com.jvcs.tracky.core.domain.notification.TimerNotificationSession
import com.jvcs.tracky.design_system.util.formatDurationHoursMinutesSeconds
import kotlin.time.Duration

/**
 * Builds the ongoing timer notification.
 *
 * Standard NotificationCompat for now; the custom RemoteViews layout that matches the design lands
 * in the next slice. The three title lines are already carried the way the design orders them.
 */
class TimerNotificationFactory(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.timer_notification_channel_name),
            // Low: the notification is a display surface, not an interruption. It still shows on
            // the lock screen and in the shade, it just never buzzes.
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.timer_notification_channel_description)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(session: TimerNotificationSession, elapsed: Duration): Notification {
        val clock = formatDurationHoursMinutesSeconds(elapsed)
        val action = if (session.isRunning) {
            NotificationCompat.Action.Builder(
                null,
                context.getString(R.string.timer_notification_pause),
                servicePendingIntent(TimerNotificationService.ACTION_PAUSE)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                null,
                context.getString(R.string.timer_notification_resume),
                servicePendingIntent(TimerNotificationService.ACTION_RESUME)
            ).build()
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setSubText(session.projectTitle)
            .setContentTitle(session.taskTitle)
            .setContentText(session.subTaskTitle?.let { "$it  ·  $clock" } ?: clock)
            .setOngoing(session.isRunning)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            // The point of the feature: the timer has to be readable without unlocking.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(session.projectColorArgb ?: DEFAULT_PROJECT_COLOR)
            .addAction(action)
            .build()
    }

    private fun servicePendingIntent(action: String): PendingIntent =
        PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, TimerNotificationService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    companion object {
        const val CHANNEL_ID = "tracky_running_timer"
        const val NOTIFICATION_ID = 1001

        /** Matches design_system.theme.defaultProjectColor, for a project that has none set. */
        private const val DEFAULT_PROJECT_COLOR = 0xFF7DA0B7.toInt()
    }
}
