package com.jvcs.tracky.core.domain.model


import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class Project @OptIn(ExperimentalTime::class) constructor(
    val projectId: String, // null if new project
    val title: String,
    val description: String?,
    val colorArgb: Int?,
    val totalDurationMillis: Long?,
    val startDateTimeUtc: Instant,
    val isFinished: Boolean,
    val endDateTimeUtc: Instant?,
    val projectSessions: List<ProjectSession>? = null
)

data class ProjectSession @OptIn(ExperimentalTime::class) constructor(
    val projectSessionId: String,
    val title: String,
    val durationMillis: Long?,
    val startDateTimeUtc: Instant,
    val endDateTimeUtc: Instant?,
    val isFinished: Boolean,
    val parentProjectId: String,
    val isTimerRunning: Boolean
)
