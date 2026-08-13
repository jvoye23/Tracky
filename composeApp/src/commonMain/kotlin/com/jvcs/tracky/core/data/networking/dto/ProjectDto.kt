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
    @SerialName("updatedAtUtc") val updatedAt: String? = null,
    val sortIndex: Long? = null
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
    // The server names these "id" and "parentTaskId"; the Kotlin side keeps the domain's names.
    // Without the @SerialName mapping every non-empty intervals array fails to decode.
    @SerialName("id") val intervalId: String,
    @SerialName("parentTaskId") val parentSessionId: String,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String? = null,
    val durationMillis: Long = 0L,
    // Stamped by the server but deliberately not carried into the domain: intervals resolve
    // conflicts by retrying a duplicate CREATE as an UPDATE, not by last-write-wins, so there is
    // nothing local to compare it against. See TaskInterval.ownUpdatedAt.
    @SerialName("updatedAtUtc") val updatedAt: String? = null
)