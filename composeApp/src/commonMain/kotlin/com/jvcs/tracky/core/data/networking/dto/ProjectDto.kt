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
    // Required and non-blank on the wire since API 1.6.0. Defaulted here rather than mandatory so a
    // response from a pre-1.6.0 deployment still decodes; toProjectTask() falls back to description
    // for those, which is where the title used to live.
    val title: String = "",
    val description: String? = null,
    val durationMillis: Long? = null,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String? = null,
    val isFinished: Boolean = false,
    val isTimerRunning: Boolean = false,
    val intervals: List<TaskIntervalDto> = emptyList(),
    // The server always sends `[]` rather than null for an empty collection, so the default only
    // covers a deployment that predates subtasks entirely.
    val subTasks: List<ProjectSubTaskDto> = emptyList(),
    @SerialName("updatedAtUtc") val updatedAt: String? = null
)

/**
 * A subtask as the server sends it — the same field set a task has, one level down, minus the
 * nested `subTasks` a task carries (subtasks do not nest further).
 *
 * Like [ProjectTaskDto] this renames nothing except `id`: the domain calls it `projectSubTaskId`
 * while the wire calls it `id`, and the parent is `parentTaskId` on the wire but
 * `parentProjectTaskId` in the domain. The project id is never repeated on the wire — it is handed
 * down from the enclosing project when mapping.
 */
@Serializable
data class ProjectSubTaskDto(
    @SerialName("id") val subTaskId: String,
    @SerialName("parentTaskId") val parentProjectTaskId: String,
    val title: String,
    val description: String? = null,
    val durationMillis: Long? = null,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String? = null,
    val isFinished: Boolean = false,
    val isTimerRunning: Boolean = false,
    val intervals: List<SubTaskIntervalDto> = emptyList(),
    // A real stamp, unlike an interval's: subtasks are edited by hand, so last-write-wins has
    // something to compare. See ProjectSubTask.ownUpdatedAt.
    @SerialName("updatedAtUtc") val updatedAt: String? = null
)

/**
 * A subtask interval as the server sends it.
 *
 * Two fields the domain needs are deliberately absent, and neither can be defaulted in:
 * `parentTaskIntervalId` (the server has no column for it yet — see
 * Requirements/backend-subtask-interval-nesting.md) and `startedParentTimer` (a purely local fact
 * about which timer opened which). [toSubTaskInterval] therefore takes both as parameters, so a
 * server echo can never silently blank them out.
 */
@Serializable
data class SubTaskIntervalDto(
    @SerialName("id") val subTaskIntervalId: String,
    @SerialName("parentSubTaskId") val parentSubTaskId: String,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String? = null,
    val durationMillis: Long = 0L,
    // Stamped by the server but not carried into the domain, for the same reason TaskIntervalDto's
    // is not: intervals resolve conflicts by retrying a duplicate CREATE as an UPDATE, not by
    // last-write-wins, so there is nothing local to compare it against.
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