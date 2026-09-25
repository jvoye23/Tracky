package com.jvcs.tracky.features.project.data.export

import com.jvcs.tracky.features.project.data.export.dto.ExportedIntervalDto
import com.jvcs.tracky.features.project.data.export.dto.ExportedProjectDto
import com.jvcs.tracky.features.project.data.export.dto.ExportedSubTaskDto
import com.jvcs.tracky.features.project.data.export.dto.ExportedTaskDto
import com.jvcs.tracky.features.project.data.export.dto.ProjectExportDto
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.models.status
import com.jvcs.tracky.features.project.domain.task.sortedBySubTaskOrder
import com.jvcs.tracky.features.project.domain.task.sortedByTaskOrder
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private const val EXPORT_FORMAT_VERSION = 1
private const val EXPORT_APP_NAME = "Tracky"
private const val RGB_MASK = 0xFFFFFF
private const val HEX_RADIX = 16
private const val RGB_HEX_DIGITS = 6

fun Project.toProjectExportDto(exportedAt: Instant, timeZone: TimeZone): ProjectExportDto =
    ProjectExportDto(
        formatVersion = EXPORT_FORMAT_VERSION,
        app = EXPORT_APP_NAME,
        exportedAt = exportedAt.toString(),
        timeZone = timeZone.id,
        project =
            ExportedProjectDto(
                id = projectId,
                title = title,
                description = description,
                color = colorArgb?.toRgbHex(),
                status = status.name.lowercase(),
                start = startDateTimeUtc.toString(),
                end = endDateTimeUtc?.toString(),
                totalDurationMs = totalDurationMillis ?: 0L,
                totalDuration = formatExportDuration(totalDurationMillis ?: 0L),
                // Null means "not loaded"; the caller is expected to hand over the full tree.
                tasks = projectTasks.orEmpty().sortedByTaskOrder().map { it.toExportedTaskDto() },
            ),
    )

private fun ProjectTask.toExportedTaskDto(): ExportedTaskDto =
    ExportedTaskDto(
        id = projectTaskId,
        title = title,
        description = description,
        isFinished = isFinished,
        start = startDateTimeUtc.toString(),
        end = endDateTimeUtc?.toString(),
        durationMs = durationMillis ?: 0L,
        duration = formatExportDuration(durationMillis ?: 0L),
        intervals = intervals.sortedBy { it.startDateTimeUtc }.map { it.toExportedIntervalDto() },
        subtasks = subTasks.orEmpty().sortedBySubTaskOrder().map { it.toExportedSubTaskDto() },
    )

private fun ProjectSubTask.toExportedSubTaskDto(): ExportedSubTaskDto =
    ExportedSubTaskDto(
        id = projectSubTaskId,
        title = title,
        description = description,
        isFinished = isFinished,
        start = startDateTimeUtc.toString(),
        end = endDateTimeUtc?.toString(),
        durationMs = durationMillis ?: 0L,
        duration = formatExportDuration(durationMillis ?: 0L),
        intervals = subTaskIntervals.sortedBy { it.startDateTimeUtc }.map { it.toExportedIntervalDto() },
    )

private fun TaskInterval.toExportedIntervalDto(): ExportedIntervalDto =
    ExportedIntervalDto(
        id = intervalId,
        start = startDateTimeUtc.toString(),
        end = endDateTimeUtc?.toString(),
        durationMs = durationMillis,
        duration = formatExportDuration(durationMillis),
    )

private fun SubTaskInterval.toExportedIntervalDto(): ExportedIntervalDto =
    ExportedIntervalDto(
        id = subTaskIntervalId,
        start = startDateTimeUtc.toString(),
        end = endDateTimeUtc?.toString(),
        durationMs = durationMillis,
        duration = formatExportDuration(durationMillis),
    )

/** `#RRGGBB`; alpha is dropped because every project color is opaque. */
private fun Int.toRgbHex(): String =
    "#" + (this and RGB_MASK).toString(HEX_RADIX).padStart(RGB_HEX_DIGITS, '0').uppercase()

/** `HH:MM:SS`, hours unwrapped and seconds truncated like the app's clocks (whose formatter is UI code). */
private fun formatExportDuration(millis: Long): String =
    millis.milliseconds.toComponents { hours, minutes, seconds, _ ->
        "${hours.toString().padStart(2, '0')}:" +
            "${minutes.toString().padStart(2, '0')}:" +
            seconds.toString().padStart(2, '0')
    }
