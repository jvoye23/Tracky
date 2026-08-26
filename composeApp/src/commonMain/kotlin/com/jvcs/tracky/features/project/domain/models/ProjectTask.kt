package com.jvcs.tracky.features.project.domain.models

import kotlin.time.Instant

data class ProjectTask(
    val projectTaskId: String,
    val title: String,
    val description: String?,
    val durationMillis: Long?,
    val startDateTimeUtc: Instant,
    val endDateTimeUtc: Instant? = null,
    val isFinished: Boolean = false,
    val parentProjectId: String,
    val isTimerRunning: Boolean,
    val intervals: List<TaskInterval> = emptyList(),
    override val ownUpdatedAt: Instant? = null,
    // null means "not loaded" (see TaskWithIntervals.toProjectTask), not "no subtasks" — the same
    // distinction Project.projectTasks draws. Intervals need no such marker: every query that
    // returns a task returns its intervals with it, so an empty list there really is empty.
    val subTasks: List<ProjectSubTask>? = null
) : Timestamped {
    override val children: List<Timestamped> get() = intervals + subTasks.orEmpty()
}
