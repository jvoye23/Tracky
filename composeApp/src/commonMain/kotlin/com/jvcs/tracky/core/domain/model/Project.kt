@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.core.domain.model


import kotlin.time.ExperimentalTime
import kotlin.time.Instant

interface Timestamped {
    val ownUpdatedAt: Instant?
    val children: List<Timestamped> get() = emptyList()
    val lastUpdatedAt: Instant?
        get() = (children.mapNotNull { it.lastUpdatedAt } +
                listOfNotNull(ownUpdatedAt)).maxOrNull()
}

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
    val sortIndex: Long? = null
) : Timestamped {
    // null projectTasks means "not loaded" (see ProjectEntity.toProject), not "no tasks".
    override val children: List<Timestamped> get() = projectTasks.orEmpty()
}

data class ProjectTask(
    val projectTaskId: String,
    val title: String,
    val durationMillis: Long?,
    val startDateTimeUtc: Instant,
    val endDateTimeUtc: Instant? = null,
    val isFinished: Boolean = false,
    val parentProjectId: String,
    val isTimerRunning: Boolean,
    val intervals: List<TaskInterval> = emptyList(),
    override val ownUpdatedAt: Instant? = null
) : Timestamped {
    override val children: List<Timestamped> get() = intervals
}

data class TaskInterval(
    val intervalId: String,
    val parentSessionId: String,
    val startDateTimeUtc: Instant,
    val endDateTimeUtc: Instant?,
    val durationMillis: Long
) : Timestamped {
    // Intervals do sync remotely, but they carry no timestamp of their own. They are written by
    // one device's timer rather than edited by hand, so a duplicate CREATE is retried as an UPDATE
    // instead of being resolved by last-write-wins — which means there is nothing to compare and
    // nothing to stamp. Staying null also keeps them out of the lastUpdatedAt roll-up.
    override val ownUpdatedAt: Instant? get() = null
}

enum class ProjectStatus {
    ACTIVE,
    FINISHED,
    ARCHIVED,
    TRASHED
}

val Project.status: ProjectStatus
    get() = when {
        trashedAt != null -> ProjectStatus.TRASHED
        isFinished -> ProjectStatus.FINISHED
        isArchived -> ProjectStatus.ARCHIVED
        else -> ProjectStatus.ACTIVE
    }
