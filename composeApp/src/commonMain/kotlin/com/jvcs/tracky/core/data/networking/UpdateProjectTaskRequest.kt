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
    // The server names these isFinished/isTimerRunning — that is what its own ProjectTaskDto sends
    // back and what UpdateSubTaskRequest already uses. Serialising them as finished/timerRunning
    // made the server ignore both, default them to false, and return that over the local row.
    val isFinished: Boolean,
    val isTimerRunning: Boolean,
)
