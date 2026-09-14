package com.jvcs.tracky.features.project.domain.timer

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The timer running right now, carrying everything an out-of-app surface needs to name it.
 *
 * The counterpart to [StrandedTimer]: that one describes an interval nothing is timing any more,
 * this one the single interval that still is. Both join an interval up to its project so a caller
 * outside the UI - a notification, a Live Activity - can render it without loading a screen.
 *
 * Timing a subtask also runs its parent task's timer, so two intervals are open at once. This
 * models that as **one** timer, named after the subtask, because that is what the user started.
 *
 * @param startedAt when the timed interval opened. Wall clock, not a monotonic mark: it is read
 *   back from the database, so it survives process death where [TimeManager] cannot.
 * @param bankedDuration what the timed entity - the subtask if there is one, else the task - had
 *   already accumulated before this interval opened. Added to the open span by [elapsedAt] so the
 *   number matches the total on the task card rather than restarting at zero every session.
 */
data class RunningTimer(
    val projectId: String,
    val projectTitle: String,
    val projectColorArgb: Int?,
    val useLightTextColor: Boolean,
    val taskId: String,
    val taskTitle: String,
    val subTaskId: String?,
    val subTaskTitle: String?,
    val startedAt: Instant,
    val bankedDuration: Duration
) {
    /** The subtask when one is being timed, else the task. What Pause has to stop. */
    val timedEntityId: String get() = subTaskId ?: taskId

    /** Never shrinks below what is already banked: a clock that went backwards yields zero. */
    fun elapsedAt(now: Instant): Duration =
        bankedDuration + (now - startedAt).coerceAtLeast(Duration.ZERO)
}
