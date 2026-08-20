package com.jvcs.tracky.features.project.domain.models

import kotlin.time.Instant

/**
 * One unit of work under a [ProjectTask], timed the same way its parent is.
 *
 * A subtask mirrors [ProjectTask] rather than being a lighter-weight checklist item: it owns a
 * duration, a running flag and its own intervals, so the same timer UI works at either level.
 */
data class ProjectSubTask(
    val projectSubTaskId: String,
    val parentProjectTaskId: String,
    // Carried alongside parentProjectTaskId so a subtask knows its whole ancestry, which backs the
    // cascading foreign key onto projects — the same reason TaskInterval denormalises it.
    val parentProjectId: String,
    val title: String,
    val description: String? = null,
    val durationMillis: Long?,
    val isTimerRunning: Boolean,
    val startDateTimeUtc: Instant,
    val endDateTimeUtc: Instant? = null,
    val isFinished: Boolean = false,
    val subTaskIntervals: List<SubTaskInterval> = emptyList(),
    // A real stamp, unlike its intervals': subtasks are edited by hand, so last-write-wins has
    // something to compare. It rolls up into the parent task and on into the project, which is what
    // makes editing a subtask count as modifying its project for the modification-date sort.
    override val ownUpdatedAt: Instant? = null,
) : Timestamped {
    override val children: List<Timestamped> get() = subTaskIntervals
}
