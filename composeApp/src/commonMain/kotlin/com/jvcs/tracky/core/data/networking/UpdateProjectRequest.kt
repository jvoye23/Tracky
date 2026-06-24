package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProjectRequest(
    val title: String,
    val description: String?,
    val color: Int?,
    val totalDuration: Long?,
    val startDateTimeUtc: String,
    val useLightTextColor: Boolean,
    val endDateTimeUtc: String?,
    val trashedAtUtc: String?,
    val isPinned: Boolean,
    val isFinished: Boolean,
    val isArchived: Boolean,
    val updatedAtUtc: String? = null,
)