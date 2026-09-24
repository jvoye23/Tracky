package com.jvcs.tracky.core.domain.notification

import com.jvcs.tracky.features.project.domain.timer.ProjectRef
import com.jvcs.tracky.features.project.domain.timer.TaskRef
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
 * @param task the task line, or the parent of [subTask] when there is one.
 * @param subTask the third line, or null when the task itself is timed. A whole ref or nothing,
 *   so a renderer never has to decide what a subtask with no title looks like.
 * @param isRunning false after Pause: the clock is frozen at [elapsed] and the button offers Play.
 * @param isForeign true when another device started this timer. A renderer must not offer Pause for
 *   one: pausing is stop-then-start, so it would stop that device's timer globally. The coordinator
 *   refuses it regardless, but a button that does nothing is worse than no button.
 */
data class TimerNotificationSession(
    val project: ProjectRef,
    val useLightTextColor: Boolean,
    val task: TaskRef,
    val subTask: TaskRef?,
    val elapsed: Duration,
    val asOf: Instant,
    val isRunning: Boolean,
    val isForeign: Boolean = false,
) {
    /**
     * What the clock reads at [now]. A running session keeps counting past the instant it was
     * built, so a renderer that cannot tick can re-read it; a paused one stays frozen.
     */
    fun elapsedAt(now: Instant): Duration =
        if (isRunning) elapsed + (now - asOf).coerceAtLeast(Duration.ZERO) else elapsed
}
