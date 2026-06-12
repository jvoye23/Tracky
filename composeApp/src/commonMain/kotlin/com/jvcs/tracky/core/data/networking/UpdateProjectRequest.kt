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
    val pinned: Boolean,
    val finished: Boolean,
    val archived: Boolean,
    val updatedAtUtc: String,
)