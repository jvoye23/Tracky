package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

/**
 * Body for PUT /api/projects/{projectId}/tasks/{taskId}/intervals/{id}.
 *
 * This is what closes a running interval: the timer stop fills in [endDateTimeUtc] and the
 * [durationMillis] measured between start and stop.
 */
@Serializable
data class UpdateTaskIntervalRequest(
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val durationMillis: Long,
)
