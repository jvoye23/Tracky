package com.jvcs.tracky.core.data.networking.dto

import com.jvcs.tracky.core.domain.model.ProjectTask
import kotlinx.serialization.Serializable

@Serializable
data class ProjectDto(
    val id: String,
    val title: String,
    val description: String?,
    val color: Int?,
    val totalDuration: Long?,
    val startDateTimeUtc: String,
    val useLightTextColor: Boolean,
    val endDateTimeUtc: String?,
    val tasks: List<ProjectTask>?,
    val finished: Boolean = false

)