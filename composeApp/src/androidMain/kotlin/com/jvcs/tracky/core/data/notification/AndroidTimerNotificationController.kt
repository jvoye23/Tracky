package com.jvcs.tracky.core.data.notification

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.jvcs.tracky.core.domain.notification.TimerNotificationController
import com.jvcs.tracky.core.domain.notification.TimerNotificationSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds what the notification should say and starts the service that says it.
 *
 * The session travels through a StateFlow rather than intent extras: the payload would otherwise
 * need to be Parcelable, and the service would have to survive being handed a stale one. Here the
 * service reads the current value and re-reads it on every change.
 */
class AndroidTimerNotificationController(
    private val context: Context
) : TimerNotificationController {

    private val _session = MutableStateFlow<TimerNotificationSession?>(null)
    val session: StateFlow<TimerNotificationSession?> = _session.asStateFlow()

    override suspend fun show(session: TimerNotificationSession) {
        val alreadyShowing = _session.value != null
        // Set first, start second: the service calls startForeground straight out of
        // onStartCommand and needs something to show within five seconds of being started.
        _session.value = session
        if (!alreadyShowing) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimerNotificationService::class.java)
            )
        }
    }

    /**
     * Puts the current notification back up.
     *
     * Android 13 withholds a foreground-service notification posted without POST_NOTIFICATIONS, and
     * granting the permission afterwards does not bring it back on its own - the service only
     * re-posts when the session changes, which for a steadily running timer may be not at all. The
     * permission requester calls this the moment a grant comes in.
     */
    fun repost() {
        if (_session.value == null) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, TimerNotificationService::class.java)
        )
    }

    override suspend fun dismiss() {
        // The service is watching; a null value is what tells it to take itself down.
        _session.value = null
    }
}
