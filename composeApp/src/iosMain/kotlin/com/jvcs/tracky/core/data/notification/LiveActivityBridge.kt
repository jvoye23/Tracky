package com.jvcs.tracky.core.data.notification

import kotlin.concurrent.Volatile

/**
 * One render of the timer Live Activity, in types that survive the trip to Swift.
 *
 * The domain payload, TimerNotificationSession, carries kotlin.time Duration and Instant, neither
 * of which has a usable Objective-C representation. So the controller flattens them here: a start
 * date for the running case, because ActivityKit's Text(timerInterval:) ticks on its own from one,
 * and a plain second count for the frozen case.
 *
 * @param startedAtEpochSeconds the instant the clock would have begun at to read [elapsedSeconds]
 *   now. Derived rather than carried, so a card that ticks needs no further updates.
 * @param elapsedSeconds what a card that cannot tick should read - the whole task or subtask
 *   lifetime, banked intervals included.
 * @param isRunning false after Pause: the clock is frozen at [elapsedSeconds] and the button
 *   offers Play.
 */
data class LiveActivityState(
    val projectId: String,
    val projectTitle: String,
    val taskTitle: String,
    val subTaskTitle: String?,
    val accentArgb: Int,
    val useLightTextColor: Boolean,
    val startedAtEpochSeconds: Double,
    val elapsedSeconds: Double,
    val isRunning: Boolean,
    /** True when another device started this timer; the widget hides its toggle for one. */
    val isForeign: Boolean = false,
)

/**
 * The Swift half of the timer notification.
 *
 * ActivityKit is unreachable from Kotlin/Native, so the app implements this in Swift and hands it
 * over at launch. Calls arrive on the main thread; [IosTimerNotificationController] guarantees that
 * much, because ActivityKit is main-thread only and the coordinator that drives it runs on
 * Dispatchers.Default.
 */
interface LiveActivityBridge {

    /** Shows the Live Activity, or updates the one already up. */
    fun show(state: LiveActivityState)

    /** Ends it. Safe to call when nothing is showing. */
    fun dismiss()
}

/**
 * Holds the [LiveActivityBridge] the Swift app registers at launch.
 *
 * Same shape and the same reason as BackgroundTaskRegistry: Koin builds the Kotlin graph from
 * startKoinIos(), which is too late for Swift to have been injected into it, so Swift pushes the
 * implementation in beforehand and Kotlin reads it back out.
 *
 * Registration has to happen before startKoinIos(), because that starts the coordinator and a
 * timer left running by a previous launch reaches the controller immediately.
 */
object LiveActivityRegistry {

    @Volatile
    private var bridge: LiveActivityBridge? = null

    /** Called from Swift in iOSApp.init(), before startKoinIos(). */
    fun register(bridge: LiveActivityBridge) {
        this.bridge = bridge
    }

    fun current(): LiveActivityBridge? = bridge
}
