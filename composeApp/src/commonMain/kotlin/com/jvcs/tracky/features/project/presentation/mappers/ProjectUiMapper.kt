package com.jvcs.tracky.features.project.presentation.mappers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.presentation.models.ProjectSubTaskUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.toLocalDateTime
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
        totalDurationMillis = totalDurationMillis ?: 0L,
        startDateTimeUtc = startDateTimeInLocalDateTime.date.format(dateTimeFormat),
        isFinished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeInLocalDateTime?.date?.format(dateTimeFormat),
        projectTasks = projectTasks?.map { it.toProjectTaskUi() },
        isPinned = isPinned
    )
}

@OptIn(ExperimentalTime::class)
fun ProjectUi.toProject(): Project {
    return Project(
        projectId = projectId ?: "",
        title = title,
        description = description,
        colorArgb = color?.toArgb(),
        totalDurationMillis = totalDurationMillis,
        startDateTimeUtc = LocalDate.parse(startDateTimeUtc, dateTimeFormat).atStartOfDayIn(TimeZone.currentSystemDefault()),
        isFinished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeUtc?.let { LocalDate.parse(it, dateTimeFormat).atStartOfDayIn(TimeZone.currentSystemDefault()) },
        projectTasks = projectTasks?.map { it.toProjectTask(projectId ?: "") },
        isArchived = false,
        trashedAt = null,
        isPinned = isPinned
    )
}

@OptIn(ExperimentalTime::class)
fun ProjectTask.toProjectTaskUi(): ProjectTaskUi {
    return ProjectTaskUi(
        projectTaskId = projectTaskId,
        title = title,
        description = description,
        durationMillis = durationMillis ?: 0L,
        formattedStateDateTime =startDateTimeUtc.toLocalDateTime(TimeZone.currentSystemDefault()).date.format(dateTimeFormat),
        formattedEndDateTimeUtc = endDateTimeUtc?.toLocalDateTime(TimeZone.currentSystemDefault())?.date?.format(dateTimeFormat) ?: "",
        isTimerRunning = isTimerRunning,
        subTasks = subTasks?.map { it.toProjectSubTaskUi() } ?: emptyList(),
        isFinished = isFinished
    )
}

@OptIn(ExperimentalTime::class)
fun ProjectTaskUi.toProjectTask(parentProjectId: String): ProjectTask {
    return ProjectTask(
        projectTaskId = projectTaskId,
        title = title,
        durationMillis = durationMillis,
        startDateTimeUtc = LocalDate.parse(formattedStateDateTime, dateTimeFormat)
            .atStartOfDayIn(TimeZone.currentSystemDefault()),
        endDateTimeUtc = if (formattedEndDateTimeUtc.isNotEmpty()) {
            LocalDate.parse(formattedEndDateTimeUtc, dateTimeFormat)
                .atStartOfDayIn(TimeZone.currentSystemDefault())
        } else null,
        isFinished = isFinished,
        parentProjectId = parentProjectId,
        isTimerRunning = isTimerRunning,
        description = description,
        subTasks = subTasks.map { it.toProjectSubTask(parentProjectId, projectTaskId) },
    )
}

fun ProjectSubTaskUi.toProjectSubTask(parentProjectId: String, parentTaskId: String): ProjectSubTask {
    return ProjectSubTask(
        projectSubTaskId = projectSubTaskId,
        parentProjectTaskId = parentTaskId,
        parentProjectId = parentProjectId,
        title = title,
        description = description,
        durationMillis = durationMillis,
        isTimerRunning = isTimerRunning,
        startDateTimeUtc = LocalDate.parse(formattedStartDateTime, dateTimeFormat)
            .atStartOfDayIn(TimeZone.currentSystemDefault()),
        endDateTimeUtc = if (!formattedEndDateTimeUtc.isNullOrEmpty()) {
            LocalDate.parse(formattedEndDateTimeUtc, dateTimeFormat)
                .atStartOfDayIn(TimeZone.currentSystemDefault())
        } else null,
        isFinished = isFinished
    )
}

fun ProjectSubTask.toProjectSubTaskUi(): ProjectSubTaskUi {
    return ProjectSubTaskUi(
        projectSubTaskId = projectSubTaskId,
        title = title,
        description = description,
        durationMillis = durationMillis ?: 0L,
        formattedStartDateTime =startDateTimeUtc.toLocalDateTime(TimeZone.currentSystemDefault()).date.format(dateTimeFormat),
        formattedEndDateTimeUtc = endDateTimeUtc?.toLocalDateTime(TimeZone.currentSystemDefault())?.date?.format(dateTimeFormat),
        isTimerRunning = isTimerRunning,
        isFinished = isFinished
    )
}
