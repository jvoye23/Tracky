package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

/** Body for PUT /api/projects/{projectId}/tasks/{taskId}/subtasks/{id}. The id lives in the path. */
@Serializable
data class UpdateSubTaskRequest(
    val title: String,
    val description: String? = null,
    val durationMillis: Long?,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val isFinished: Boolean,
    val isTimerRunning: Boolean,
)
