@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.core.data.networking.mappers

import com.jvcs.tracky.core.data.networking.dto.ProjectDto
import com.jvcs.tracky.core.data.networking.dto.ProjectTaskDto
import com.jvcs.tracky.core.data.networking.dto.TaskIntervalDto
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun ProjectDto.toProject(): Project {
    return Project(
        projectId = id,
        title = title,
        description = description,
        colorArgb = color,
        totalDurationMillis = totalDuration,
        startDateTimeUtc = Instant.parse(startDateTimeUtc),
        isFinished = finished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeUtc?.let(Instant::parse),
        projectTasks = tasks?.map { it.toProjectTask(id) } ?: emptyList(),
        isArchived = isArchived,
        trashedAt = trashedAt?.let(Instant::parse),
        isPinned = isPinned,
        updatedAt = updatedAt?.let(Instant::parse)
    )
}

fun ProjectTaskDto.toProjectTask(parentProjectId: String): ProjectTask {
    return ProjectTask(
        projectTaskId = id,
        title = description ?: "",
        durationMillis = durationMillis,
        startDateTimeUtc = Instant.parse(startDateTimeUtc),
        endDateTimeUtc = endDateTimeUtc?.let(Instant::parse),
        isFinished = isFinished,
        parentProjectId = parentProjectId,
        isTimerRunning = isTimerRunning,
        intervals = intervals.map { it.toTaskInterval() },
        updatedAt = updatedAt?.let(Instant::parse)
    )
}

fun TaskIntervalDto.toTaskInterval(): TaskInterval {
    return TaskInterval(
        intervalId = intervalId,
        parentSessionId = parentSessionId,
        startDateTimeUtc = Instant.parse( startDateTimeUtc),
        endDateTimeUtc = endDateTimeUtc?.let(Instant::parse),
        durationMillis = durationMillis
    )
}

fun Project.toProjectDto(): ProjectDto {
    return ProjectDto(
        id = projectId,
        title = title,
        description = description,
        color = colorArgb,
        totalDuration = totalDurationMillis,
        startDateTimeUtc = startDateTimeUtc.toString(),
        finished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeUtc?.toString(),
        tasks = projectTasks?.map { it.toProjectTaskDto() },
        isArchived = isArchived,
        trashedAt = trashedAt?.toString(),
        isPinned = isPinned,
        updatedAt = updatedAt?.toString()
    )
}

fun ProjectTask.toProjectTaskDto(): ProjectTaskDto {
    return ProjectTaskDto(
        id = projectTaskId,
        description = title,
        durationMillis = durationMillis,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        isFinished = isFinished,
        isTimerRunning = isTimerRunning,
        intervals = intervals.map { it.toTaskIntervalDto() },
        updatedAt = updatedAt?.toString()
    )
}

fun TaskInterval.toTaskIntervalDto(): TaskIntervalDto {
    return TaskIntervalDto(
        intervalId = intervalId,
        parentSessionId = parentSessionId,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        durationMillis = durationMillis
    )
}