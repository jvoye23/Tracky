package com.jvcs.tracky.core.domain.model


import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val projectId: String,
    val title: String,
    val description: String?,
    val colorArgb: Int?,
    val totalDurationMillis: Long?,
    val startDateTimeUtc: String,
    val isFinished: Boolean,
    val useLightTextColor: Boolean = false,
    val endDateTimeUtc: String?,
    val projectTasks: List<ProjectTask>? = null
)

@Serializable
data class ProjectTask(
    val projectTaskId: String,
    val title: String,
    val durationMillis: Long?,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val isFinished: Boolean,
    val parentProjectId: String,
    val isTimerRunning: Boolean,
    val intervals: List<TaskInterval> = emptyList()
)

@Serializable
data class TaskInterval(
    val intervalId: String,
    val parentSessionId: String,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val durationMillis: Long
)
