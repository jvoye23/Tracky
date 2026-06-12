@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.core.domain.model


import kotlin.time.Clock
import kotlin.time.ExperimentalTime
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
    val updatedAt: Instant = Clock.System.now(),
)

data class ProjectWithTask(
    val project: Project,
    val projectTasks: List<ProjectTask>
)

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
    val updatedAt: Instant = Clock.System.now(),
)

data class TaskInterval(
    val intervalId: String,
    val parentSessionId: String,
    val startDateTimeUtc: Instant,
    val endDateTimeUtc: Instant?,
    val durationMillis: Long
)
