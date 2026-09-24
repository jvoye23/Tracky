package com.jvcs.tracky.core.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.SystemClock
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.jvcs.tracky.composeapp.R
import com.jvcs.tracky.core.domain.notification.TimerNotificationSession
import kotlin.time.Duration

/**
 * Builds the ongoing timer notification from the custom layouts.
 *
 * setContentTitle/Text are still set even though the custom views replace them: they are what the
 * system header, Android Auto, Wear and any other renderer that ignores RemoteViews fall back to.
 */
class TimerNotificationFactory(private val context: Context) {

    fun createChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.timer_notification_channel_name),
                // Low: the notification is a display surface, not an interruption. It still shows on
                // the lock screen and in the shade, it just never buzzes.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.timer_notification_channel_description)
                setShowBadge(false)
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(session: TimerNotificationSession, elapsed: Duration): Notification {
        val button = timerButton(session)
        return NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setCustomContentView(collapsedView(session, elapsed, button))
            .setCustomBigContentView(expandedView(session, elapsed, button))
            // What the system header and any fallback renderer show.
            .setContentTitle(session.task.title)
            .setContentText(session.subTask?.title ?: session.project.title)
            .setOngoing(session.isRunning)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            // The point of the feature: the timer has to be readable without unlocking.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(button.accent)
            .setContentIntent(contentPendingIntent(session.project.id))
            .build()
    }

    /** What both layouts' pause/resume button shows and sends, and the colours it is drawn in. */
    private class TimerButton(
        val accent: Int,
        val onAccent: Int,
        val action: String,
        val icon: Int,
        val label: String,
    )

    private fun timerButton(session: TimerNotificationSession): TimerButton =
        TimerButton(
            accent = session.project.colorArgb ?: DEFAULT_PROJECT_COLOR,
            // The project supplies the accent, and the project already knows whether its own colour
            // needs light text on top - the same flag the task cards read.
            onAccent = if (session.useLightTextColor) ON_ACCENT_LIGHT else ON_ACCENT_DARK,
            action =
                if (session.isRunning) {
                    TimerNotificationService.ACTION_PAUSE
                } else {
                    TimerNotificationService.ACTION_RESUME
                },
            icon = if (session.isRunning) R.drawable.ic_pause else R.drawable.ic_play,
            label =
                context.getString(
                    if (session.isRunning) {
                        R.string.timer_notification_pause
                    } else {
                        R.string.timer_notification_resume
                    },
                ),
        )

    private fun collapsedView(
        session: TimerNotificationSession,
        elapsed: Duration,
        button: TimerButton,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_timer_collapsed).apply {
            setTextViewText(R.id.timer_collapsed_title, (session.subTask ?: session.task).title)
            clock(
                R.id.timer_collapsed_chronometer,
                R.id.timer_collapsed_clock,
                session.isRunning,
                elapsed,
                button.accent,
            )

            // Pause is stop-then-start, so pausing a timer another device is running would stop
            // it globally. The coordinator refuses that; hiding the button is how the user finds
            // out, instead of tapping something that silently does nothing.
            setViewVisibility(
                R.id.timer_collapsed_button,
                if (session.isForeign) View.GONE else View.VISIBLE,
            )
            setImageViewResource(R.id.timer_collapsed_button, button.icon)
            tint(R.id.timer_collapsed_button, button.accent)
            setInt(R.id.timer_collapsed_button, "setColorFilter", button.onAccent)

            setContentDescription(R.id.timer_collapsed_button, button.label)
            setOnClickPendingIntent(R.id.timer_collapsed_button, servicePendingIntent(button.action))
        }

    private fun expandedView(
        session: TimerNotificationSession,
        elapsed: Duration,
        button: TimerButton,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_timer_expanded).apply {
            setTextViewText(R.id.timer_project, session.project.title)
            setTextViewText(R.id.timer_task, session.task.title)
            setTextViewText(R.id.timer_subtask, session.subTask?.title.orEmpty())
            // Two lines when a task is timed directly, three when a subtask is.
            setViewVisibility(
                R.id.timer_subtask,
                if (session.subTask == null) View.GONE else View.VISIBLE,
            )
            clock(R.id.timer_chronometer, R.id.timer_clock, session.isRunning, elapsed, button.accent)

            setViewVisibility(
                R.id.timer_button,
                if (session.isForeign) View.GONE else View.VISIBLE,
            )
            setImageViewResource(R.id.timer_button_icon, button.icon)
            setInt(R.id.timer_button_icon, "setColorFilter", button.onAccent)

            setTextViewText(R.id.timer_button_label, button.label)
            setTextColor(R.id.timer_button_label, button.onAccent)
            tint(R.id.timer_button, button.accent)

            setContentDescription(R.id.timer_button, button.label)
            setOnClickPendingIntent(R.id.timer_button, servicePendingIntent(button.action))
        }

    /**
     * Shows one of a layout's two clocks. While running, SystemUI ticks the Chronometer from a base
     * on elapsedRealtime, so nothing re-posts the notification every second. Paused shows the
     * TextView instead: a stopped Chronometer recounts from its base whenever the shade re-inflates
     * it, and would appear to keep running. Both use DateUtils' MM:SS / H:MM:SS, the only format a
     * Chronometer can show, so Pause does not change the format.
     */
    private fun RemoteViews.clock(
        chronometerId: Int,
        textId: Int,
        isRunning: Boolean,
        elapsed: Duration,
        color: Int,
    ) {
        if (isRunning) {
            val base = SystemClock.elapsedRealtime() - elapsed.inWholeMilliseconds
            setChronometer(chronometerId, base, null, true)
        } else {
            setChronometer(chronometerId, 0L, null, false)
            setTextViewText(textId, DateUtils.formatElapsedTime(elapsed.inWholeSeconds))
        }
        setTextColor(chronometerId, color)
        setTextColor(textId, color)
        setViewVisibility(chronometerId, if (isRunning) View.VISIBLE else View.GONE)
        setViewVisibility(textId, if (isRunning) View.GONE else View.VISIBLE)
    }

    /** The pill is a white shape drawable, so the project's colour arrives as a tint. */
    private fun RemoteViews.tint(viewId: Int, color: Int) =
        setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color))

    /**
     * Opens the project that owns the running timer. Resolved through the package manager rather
     * than naming the activity: MainActivity lives in the app module, which depends on this one.
     */
    private fun contentPendingIntent(projectId: String): PendingIntent? {
        val launch =
            context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    // singleTop plus CLEAR_TOP so a tap reaches the running activity through
                    // onNewIntent instead of building a second copy of it.
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(TimerNotificationIntents.EXTRA_PROJECT_ID, projectId)
                }
                ?: return null

        return PendingIntent.getActivity(
            context,
            projectId.hashCode(),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun servicePendingIntent(action: String): PendingIntent =
        PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, TimerNotificationService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
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
