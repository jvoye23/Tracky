package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

/**
 * Body for PUT .../subtasks/{subTaskId}/intervals/{id}.
 *
 * This is what closes a running subtask interval: the timer stop fills in [endDateTimeUtc] and the
 * [durationMillis] measured between start and stop.
 */
@Serializable
data class UpdateSubTaskIntervalRequest(
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val durationMillis: Long,
)
