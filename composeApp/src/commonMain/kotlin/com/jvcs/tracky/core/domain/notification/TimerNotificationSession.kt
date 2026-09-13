package com.jvcs.tracky.core.domain.notification

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * One render of the timer notification: the three title lines, the clock, and which way the
 * button points.
 *
 * [elapsed] is paired with [asOf] rather than sent as a string because the platforms tick on their
 * own - an Android chronometer wants a base, a Live Activity wants a start date - and both need to
 * know which instant the number was true at to derive one. A renderer that cannot tick can format
 * [elapsed] directly and take the small drift.
 *
 * @param isRunning false after Pause: the clock is frozen at [elapsed] and the button offers Play.
 */
data class TimerNotificationSession(
    val projectId: String,
    val projectTitle: String,
    val projectColorArgb: Int?,
    val useLightTextColor: Boolean,
    val taskTitle: String,
    val subTaskTitle: String?,
    val elapsed: Duration,
    val asOf: Instant,
    val isRunning: Boolean
)
