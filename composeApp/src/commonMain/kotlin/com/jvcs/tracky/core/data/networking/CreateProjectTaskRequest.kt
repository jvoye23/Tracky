package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

/**
 * Body for POST /api/projects/{projectId}/tasks.
 *
 * [title] is required and must be non-blank — API 1.6.0 added it as a `@NotBlank` field, and a
 * request without it is rejected with 400. [description] is the task's *other* text, optional on
 * the wire and null until the user writes one.
 */
@Serializable
data class CreateProjectTaskRequest(
    val id: String,
    val title: String,
    val description: String? = null,
    val durationMillis: Long,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val finished: Boolean,
    val timerRunning: Boolean,
)
