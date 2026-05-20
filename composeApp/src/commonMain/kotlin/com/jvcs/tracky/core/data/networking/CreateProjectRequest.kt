package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

@Serializable
data class CreateProjectRequest(
    val id: String,
    val title: String,
    val description: String,
    val color: Int,
    val startDateTimeUtc: String,
    val useLightTextColor: Boolean,
)