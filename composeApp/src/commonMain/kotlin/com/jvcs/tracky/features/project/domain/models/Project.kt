package com.jvcs.tracky.features.project.domain.models

import kotlin.time.Instant

data class Project(
    val projectId: String,
    val title: String,
    val description: String?,
    val colorArgb: Int?,
    val totalDurationMillis: Long?,
    val startDateTimeUtc: Instant,
    val isFinished: Boolean,
    val useLightTextColor: Boolean = false,
    val endDateTimeUtc: Instant?,
    val projectTasks: List<ProjectTask>? = null,
    val isArchived: Boolean = false,
    val trashedAt: Instant? = null,
    val isPinned: Boolean = false,
    override val ownUpdatedAt: Instant? = null,
    val sortIndex: Long? = null,
    // Derived, never stored or synced: the end of the last closed task interval that banked time
    // (see lastTrackedEnd). The list reads fill it from an aggregate query, since their tasks
    // arrive without intervals; the tree read derives it from the intervals it already has.
    val lastTrackedEndUtc: Instant? = null,
) : Timestamped {

    // null projectTasks means "not loaded" (see ProjectEntity.toProject), not "no tasks".
    override val children: List<Timestamped> get() = projectTasks.orEmpty()
}
