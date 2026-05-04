package com.jvcs.tracky.core.presentation.mapper

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectSession
import com.jvcs.tracky.core.presentation.model.ProjectSessionUi
import com.jvcs.tracky.core.presentation.model.ProjectUi
import com.jvcs.tracky.design_system.util.formatDuration
import com.jvcs.tracky.design_system.util.parseDuration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

private val dateTimeFormat = LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    chars(", ")
    day()
    chars(", ")
    year()
}

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
        startDateTimeUtc = startDateTimeInLocalDateTime.date.format(dateTimeFormat),
        isFinished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeInLocalDateTime?.date?.format(dateTimeFormat),
        projectSessions = projectSessions?.map { it.toProjectSessionUi() }
    )
}

@OptIn(ExperimentalTime::class)
fun ProjectUi.toProject(): Project {
    return Project(
        projectId = projectId ?: "",
        title = title,
        description = description,
        colorArgb = color?.toArgb(),
        totalDurationMillis = parseDuration(totalDuration).inWholeMilliseconds,
        startDateTimeUtc = LocalDate.parse(startDateTimeUtc, dateTimeFormat).atStartOfDayIn(TimeZone.currentSystemDefault()),
        isFinished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeUtc?.let { LocalDate.parse(it, dateTimeFormat).atStartOfDayIn(TimeZone.currentSystemDefault()) },
        projectSessions = projectSessions?.map { it.toProjectSession(projectId ?: "") }
    )
}

fun ProjectSession.toProjectSessionUi(): ProjectSessionUi {
    return ProjectSessionUi(
        id = projectSessionId,
        title = title,
        formattedDuration = formatDuration(durationMillis?.milliseconds ?: Duration.ZERO),
        formattedStateDateTime = startDateTimeUtc.toLocalDateTime(TimeZone.currentSystemDefault()).date.format(dateTimeFormat),
        formattedEndDateTimeUtc = endDateTimeUtc?.toLocalDateTime(TimeZone.currentSystemDefault())?.date?.format(dateTimeFormat) ?: "",
        isTimerRunning = isTimerRunning
    )
}

@OptIn(ExperimentalTime::class)
fun ProjectSessionUi.toProjectSession(parentProjectId: String): ProjectSession {
    return ProjectSession(
        projectSessionId = id ?: "",
        title = title,
        durationMillis = parseDuration(formattedDuration).inWholeMilliseconds,
        startDateTimeUtc = LocalDate.parse(formattedStateDateTime, dateTimeFormat).atStartOfDayIn(TimeZone.currentSystemDefault()),
        endDateTimeUtc = if (formattedEndDateTimeUtc.isNotEmpty()) {
            LocalDate.parse(formattedEndDateTimeUtc, dateTimeFormat).atStartOfDayIn(TimeZone.currentSystemDefault())
        } else null,
        isFinished = !isTimerRunning,
        parentProjectId = parentProjectId,
        isTimerRunning = isTimerRunning
    )
}
