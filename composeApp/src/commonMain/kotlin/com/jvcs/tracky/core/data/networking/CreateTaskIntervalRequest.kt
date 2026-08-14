package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

/**
 * Body for POST /api/projects/{projectId}/tasks/{taskId}/intervals.
 *
 * [id] is the client-generated UUID Room already assigned, so the same interval keeps one identity
 * on both sides and a replayed create is detectable as a 409 rather than landing twice.
 * A null [endDateTimeUtc] means the interval is still open (the timer is running).
 */
@Serializable
data class CreateTaskIntervalRequest(
    val id: String,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val durationMillis: Long,
)
