package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

/**
 * Body for POST /api/projects/{projectId}/tasks/{taskId}/subtasks/{subTaskId}/intervals.
 *
 * A null [endDateTimeUtc] means the interval is still open (the timer is running).
 *
 * `parentTaskIntervalId` and `startedParentTimer` are deliberately absent: the server has no column
 * for either (see Requirements/backend-subtask-interval-nesting.md), and sending an unknown
 * property risks a 400. They stay local until that request lands.
 */
@Serializable
data class CreateSubTaskIntervalRequest(
    val id: String,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val durationMillis: Long,
)
