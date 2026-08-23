package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

/**
 * Body for POST /api/projects/{projectId}/tasks/{taskId}/subtasks/{subTaskId}/intervals.
 *
 * A null [endDateTimeUtc] means the interval is still open (the timer is running).
 *
 * [parentTaskIntervalId] is required, and must name an interval of the same task as the subtask's
 * parent — a mismatch is a 400, an unknown id a 404. It is immutable afterwards, which is why
 * UpdateSubTaskIntervalRequest omits it.
 *
 * `startedParentTimer` stays absent: which timer opened which is a purely local fact the server has
 * no column for.
 */
@Serializable
data class CreateSubTaskIntervalRequest(
    val id: String,
    val parentTaskIntervalId: String,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val durationMillis: Long,
)
