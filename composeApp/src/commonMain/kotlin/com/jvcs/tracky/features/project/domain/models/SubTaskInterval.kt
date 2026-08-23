package com.jvcs.tracky.features.project.domain.models

import kotlin.time.Instant

/**
 * One stretch of tracked time under a [ProjectSubTask], always nested inside a [TaskInterval].
 */
data class SubTaskInterval(
    val subTaskIntervalId: String,
    // Non-null on purpose: timing a subtask also runs its parent task's timer, so every subtask
    // interval sits inside exactly one task interval. This backs a cascading foreign key onto
    // task_intervals — deleting the enclosing task interval takes its subtask intervals with it.
    val parentTaskIntervalId: String,
    val parentSubTaskId: String,
    // Denormalised for the same cascade-safety reason TaskInterval carries it: it makes the project
    // a direct parent of the interval, so a project delete reaches it even if the rows in between
    // were ever to disappear on their own.
    val parentProjectId: String,
    val startDateTimeUtc: Instant,
    val endDateTimeUtc: Instant?,
    val durationMillis: Long
) : Timestamped {
    // No stamp of its own, for the same reason TaskInterval has none: an interval is written by a
    // device's timer rather than edited by hand, so there is nothing to compare and nothing to
    // stamp. Staying null also keeps it out of the lastUpdatedAt roll-up.
    override val ownUpdatedAt: Instant? get() = null
}
