package com.jvcs.tracky.core.data.networking.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProjectDto(
    val id: String,
    val title: String,
    val description: String?,
    val color: Int?,
    val totalDuration: Long?,
    val startDateTimeUtc: String,
    val useLightTextColor: Boolean,
    val endDateTimeUtc: String? = null,
    val tasks: List<ProjectTaskDto>?,
    val finished: Boolean = false,
    val isArchived: Boolean = false,
    val trashedAt: String? = null,
    val isPinned: Boolean = false
)

@Serializable
data class ProjectTaskDto(
    val id: String,
    val description: String? = null,
    val durationMillis: Long? = null,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String? = null,
    val isFinished: Boolean = false,
    val isTimerRunning: Boolean = false,
    val intervals: List<TaskIntervalDto> = emptyList()
)


@Serializable
data class TaskIntervalDto(
    val intervalId: String,
    val parentSessionId: String,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val durationMillis: Long

)