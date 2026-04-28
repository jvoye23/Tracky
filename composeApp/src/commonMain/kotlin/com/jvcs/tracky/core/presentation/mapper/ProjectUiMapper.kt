package com.jvcs.tracky.core.presentation.mapper

import androidx.compose.ui.graphics.Color
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectSession
import com.jvcs.tracky.core.presentation.model.ProjectSessionUi
import com.jvcs.tracky.core.presentation.model.ProjectUi
import com.jvcs.tracky.design_system.util.formatDuration
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

@OptIn(ExperimentalTime::class)
fun Project.toProjectUi(): ProjectUi {

    val startDateTimeInLocalDateTime = startDateTimeUtc
        .toLocalDateTime(TimeZone.currentSystemDefault())

    val endDateTimeInLocalDateTime = endDateTimeUtc
        ?.toLocalDateTime(TimeZone.currentSystemDefault())

    return ProjectUi(
        projectId = projectId,
        title = title,
        description = description,
        color = if (colorArgb != null) Color(colorArgb) else null,
        totalDuration = formatDuration(totalDurationMillis?.milliseconds ?: Duration.ZERO),
        startDateTimeUtc = startDateTimeInLocalDateTime.toString(),
        isFinished = isFinished,
        endDateTimeUtc = endDateTimeInLocalDateTime.toString(),
        projectSessions = projectSessions?.map { it.toProjectSessionUi() }
    )
}

fun ProjectSession.toProjectSessionUi(): ProjectSessionUi {
    return ProjectSessionUi(
        id = projectSessionId,
        title = title,
        formattedDuration = formatDuration(durationMillis?.milliseconds ?: Duration.ZERO),
        formattedStateDateTime = startDateTimeUtc.toLocalDateTime(TimeZone.currentSystemDefault()).toString(),
        formattedEndDateTimeUtc = endDateTimeUtc?.toLocalDateTime(TimeZone.currentSystemDefault()).toString(),
        isTimerRunning = isTimerRunning
    )
}