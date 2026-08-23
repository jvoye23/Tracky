package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

/** Body for PUT /api/projects/{projectId}/tasks/{taskId}. See [CreateProjectTaskRequest] on [title]. */
@Serializable
data class UpdateProjectTaskRequest(
    val title: String,
    val description: String? = null,
    val durationMillis: Long,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val finished: Boolean,
    val timerRunning: Boolean,
)
