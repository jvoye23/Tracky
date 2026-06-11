package com.jvcs.tracky.core.data.networking.dto

import kotlinx.serialization.SerialName
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
    val tasks: List<ProjectTaskDto>? = null,
    @SerialName("isFinished") val finished: Boolean = false,
    val isArchived: Boolean = false,
    @SerialName("trashedAtUtc") val trashedAt: String? = null,
    val isPinned: Boolean = false,
    @SerialName("updatedAtUtc") val updatedAt: String? = null
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
    val intervals: List<TaskIntervalDto> = emptyList(),
    @SerialName("updatedAtUtc") val updatedAt: String? = null
)


@Serializable
data class TaskIntervalDto(
    val intervalId: String,
    val parentSessionId: String,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val durationMillis: Long

)