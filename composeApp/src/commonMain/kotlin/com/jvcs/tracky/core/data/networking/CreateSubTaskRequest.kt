package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

/**
 * Body for POST /api/projects/{projectId}/tasks/{taskId}/subtasks.
 *
 * [id] is the client-generated UUID Room already assigned, so the same subtask keeps one identity
 * on both sides and a replayed create is detectable as a 409 rather than landing twice.
 *
 * The parent project is derived from the parent task server-side, so it is never sent. [title] must
 * be non-blank — the same `@NotBlank` rule tasks have. [description] is the subtask's *other* text,
 * which the domain does model, unlike a task's.
 */
@Serializable
data class CreateSubTaskRequest(
    val id: String,
    val title: String,
    val description: String? = null,
    val durationMillis: Long?,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String?,
    val isFinished: Boolean,
    val isTimerRunning: Boolean,
)
