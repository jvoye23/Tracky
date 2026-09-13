package com.jvcs.tracky.features.project.domain.timer

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * One timer the app found still running at start-up, waiting for the user to say what it was worth.
 *
 * A task interval and the subtask interval nested inside it are **one** item, not two. A task that
 * owns subtasks counts only its subtasks' intervals (see `countedDayIntervals` rule 1), so
 * resolving the two levels separately would let one count and the other not.
 *
 * @param taskIntervalId the stranded task interval, or null for a subtask interval whose parent is
 *   already closed — possible through a bad pull, and it still has to be resolvable.
 * @param subTaskIntervalId the stranded subtask interval inside it, or null when the task was timed
 *   directly.
 * @param proposedEndAt when the app noticed. Frozen at detection, so the offered duration does not
 *   grow while the dialog sits unanswered.
 */
data class StrandedTimer(
    val taskIntervalId: String?,
    val subTaskIntervalId: String?,
    val taskId: String,
    val taskTitle: String,
    val projectTitle: String,
    val subTaskTitle: String?,
    val startedAt: Instant,
    val proposedEndAt: Instant,
    /**
     * True when this is a task-level interval on a task that owns subtasks. Keeping it banks time
     * that no per-day view will ever render — rule 1 counts only the subtasks — while still
     * inflating the task and project totals. The timer never opens such an interval deliberately,
     * so its existence is itself evidence of the bug; the dialog says so and defaults to discard.
     */
    val keepingWouldNotBeCounted: Boolean = false
) {
    /** What [proposedEndAt] is worth. Never negative: a clock that went backwards yields zero. */
    val proposedDuration: Duration
        get() = (proposedEndAt - startedAt).coerceAtLeast(Duration.ZERO)

    /** Identifies the item across a list rebuild. The subtask interval is the more specific half. */
    val id: String get() = subTaskIntervalId ?: taskIntervalId.orEmpty()
}
