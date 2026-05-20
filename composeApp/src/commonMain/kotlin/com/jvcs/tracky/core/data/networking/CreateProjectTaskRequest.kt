package com.jvcs.tracky.core.data.networking

import com.jvcs.tracky.core.data.networking.dto.TaskIntervalDto
import com.jvcs.tracky.core.domain.model.TaskInterval
import kotlinx.serialization.Serializable

@Serializable
data class CreateProjectTaskRequest(
    val id: String,
    val durationMillis: Long,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val intervals: List<TaskIntervalDto>,
    val finished: Boolean,
    val timerRunning: Boolean
)
