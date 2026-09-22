package com.jvcs.tracky.core.data.notification

import androidx.compose.ui.graphics.toArgb
import com.jvcs.tracky.core.domain.notification.TimerNotificationController
import com.jvcs.tracky.core.domain.notification.TimerNotificationSession
import com.jvcs.tracky.design_system.theme.defaultProjectColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows the running timer as an iOS Live Activity.
 *
 * There is no home-screen surface on iOS: this reaches the Lock Screen, the Dynamic Island and
 * StandBy, and nothing else.
 *
 * The rendering itself is Swift's - see [LiveActivityBridge]. This end owns the mapping and the
 * threading, and does nothing at all until Swift has registered a bridge.
 */
class IosTimerNotificationController(
    private val bridge: () -> LiveActivityBridge? = LiveActivityRegistry::current
) : TimerNotificationController {

    override suspend fun show(session: TimerNotificationSession) {
        val state = session.toLiveActivityState()
        // ActivityKit is main-thread only, and TimerNotificationCoordinator collects on AppScope,
        // which is Dispatchers.Default.
        withContext(Dispatchers.Main) { bridge()?.show(state) }
    }

    override suspend fun dismiss() {
        withContext(Dispatchers.Main) { bridge()?.dismiss() }
    }
}

/**
 * Flattens a session into what Swift can render.
 *
 * The accent rules are the Android factory's, kept deliberately identical: the project supplies the
 * colour, and the project already knows whether its own colour needs light text on top - the same
 * flag the task cards read.
 */
internal fun TimerNotificationSession.toLiveActivityState(): LiveActivityState {
    val elapsedSeconds = elapsed.inWholeSeconds.toDouble()
    return LiveActivityState(
        projectId = project.id,
        projectTitle = project.title,
        taskTitle = task.title,
        subTaskTitle = subTask?.title,
        accentArgb = project.colorArgb ?: defaultProjectColor.toArgb(),
        useLightTextColor = useLightTextColor,
        // Where the clock must have started to read `elapsed` at `asOf`. A running card ticks from
        // this by itself and never needs another update.
        startedAtEpochSeconds = asOf.epochSeconds.toDouble() - elapsedSeconds,
        elapsedSeconds = elapsedSeconds,
        isRunning = isRunning,
        isForeign = isForeign
    )
}
