package com.jvcs.tracky.core.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.jvcs.tracky.composeapp.R
import com.jvcs.tracky.core.domain.notification.TimerNotificationSession
import com.jvcs.tracky.design_system.util.formatDurationHoursMinutesSeconds
import kotlin.time.Duration

/**
 * Builds the ongoing timer notification from the custom layouts.
 *
 * setContentTitle/Text are still set even though the custom views replace them: they are what the
 * system header, Android Auto, Wear and any other renderer that ignores RemoteViews fall back to.
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
        val accent = session.projectColorArgb ?: DEFAULT_PROJECT_COLOR
        // The project supplies the accent, and the project already knows whether its own colour
        // needs light text on top - the same flag the task cards read.
        val onAccent = if (session.useLightTextColor) ON_ACCENT_LIGHT else ON_ACCENT_DARK
        val clock = formatDurationHoursMinutesSeconds(elapsed)
        val action = if (session.isRunning) TimerNotificationService.ACTION_PAUSE
        else TimerNotificationService.ACTION_RESUME
        val icon = if (session.isRunning) R.drawable.ic_pause else R.drawable.ic_play
        val label = context.getString(
            if (session.isRunning) R.string.timer_notification_pause
            else R.string.timer_notification_resume
        )

        val collapsed = RemoteViews(context.packageName, R.layout.notification_timer_collapsed).apply {
            setTextViewText(R.id.timer_collapsed_title, session.subTaskTitle ?: session.taskTitle)
            setTextViewText(R.id.timer_collapsed_clock, clock)
            setTextColor(R.id.timer_collapsed_clock, accent)
            setImageViewResource(R.id.timer_collapsed_button, icon)
            tint(R.id.timer_collapsed_button, accent)
            setInt(R.id.timer_collapsed_button, "setColorFilter", onAccent)
            setContentDescription(R.id.timer_collapsed_button, label)
            setOnClickPendingIntent(R.id.timer_collapsed_button, servicePendingIntent(action))
        }

        val expanded = RemoteViews(context.packageName, R.layout.notification_timer_expanded).apply {
            setTextViewText(R.id.timer_project, session.projectTitle)
            setTextViewText(R.id.timer_task, session.taskTitle)
            setTextViewText(R.id.timer_subtask, session.subTaskTitle.orEmpty())
            // Two lines when a task is timed directly, three when a subtask is.
            setViewVisibility(
                R.id.timer_subtask,
                if (session.subTaskTitle == null) View.GONE else View.VISIBLE
            )
            setTextViewText(R.id.timer_clock, clock)
            setTextColor(R.id.timer_clock, accent)
            setImageViewResource(R.id.timer_button_icon, icon)
            setInt(R.id.timer_button_icon, "setColorFilter", onAccent)
            setTextViewText(R.id.timer_button_label, label)
            setTextColor(R.id.timer_button_label, onAccent)
            tint(R.id.timer_button, accent)
            setContentDescription(R.id.timer_button, label)
            setOnClickPendingIntent(R.id.timer_button, servicePendingIntent(action))
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            // What the system header and any fallback renderer show.
            .setContentTitle(session.taskTitle)
            .setContentText(session.subTaskTitle ?: session.projectTitle)
            .setOngoing(session.isRunning)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            // The point of the feature: the timer has to be readable without unlocking.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(accent)
            .build()
    }

    /** The pill is a white shape drawable, so the project's colour arrives as a tint. */
    private fun RemoteViews.tint(viewId: Int, color: Int) =
        setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color))

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
        private const val ON_ACCENT_LIGHT = 0xFFFFFFFF.toInt()
        private const val ON_ACCENT_DARK = 0xFF1A1C1E.toInt()
    }
}
