package com.jvcs.tracky.features.project.domain.models

import kotlin.time.Instant

data class ProjectTask(
    val projectTaskId: String,
    val title: String,
    val durationMillis: Long?,
    val startDateTimeUtc: Instant,
    val endDateTimeUtc: Instant? = null,
    val isFinished: Boolean = false,
    val parentProjectId: String,
    val isTimerRunning: Boolean,
    val intervals: List<TaskInterval> = emptyList(),
    override val ownUpdatedAt: Instant? = null
) : Timestamped {
    override val children: List<Timestamped> get() = intervals
}