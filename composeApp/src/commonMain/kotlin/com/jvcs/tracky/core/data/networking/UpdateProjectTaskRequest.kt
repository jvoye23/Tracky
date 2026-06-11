package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProjectTaskRequest(
    val description: String,
    val durationMillis: Long,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val finished: Boolean,
    val timerRunning: Boolean,
)
