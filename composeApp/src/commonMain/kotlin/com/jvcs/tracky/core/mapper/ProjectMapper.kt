@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.core.mapper

import com.jvcs.tracky.core.data.networking.CreateProjectRequest
import com.jvcs.tracky.core.data.networking.CreateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.UpdateProjectRequest
import com.jvcs.tracky.core.data.networking.UpdateProjectTaskRequest
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTasksEntity
import com.jvcs.tracky.core.database.relation.TaskWithIntervals
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun Project.toProjectEntity(): ProjectEntity {
    return ProjectEntity(
        projectId = projectId,
        title = title,
        description = description,
        color = colorArgb,
        totalDuration = totalDurationMillis,
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        isFinished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
        isArchived = isArchived,
        trashedAtEpochMs = trashedAt?.toEpochMilliseconds(),
        isPinned = isPinned,
        updatedAtEpochMs = updatedAt?.toEpochMilliseconds() ?: 0L,
    )
}

fun ProjectEntity.toProject(): Project {
    return Project(
        projectId = projectId,
        title = title,
        description = description,
        colorArgb = color,
        totalDurationMillis = totalDuration,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        isFinished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        isArchived = isArchived,
        trashedAt = trashedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        isPinned = isPinned,
        updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMs),
    )
}

fun ProjectWithTasksEntity.toProject(): Project {
    return Project(
        projectId = project.projectId,
        title = project.title,
        description = project.description,
        colorArgb = project.color,
        totalDurationMillis = project.totalDuration,
        startDateTimeUtc = Instant.fromEpochMilliseconds(project.startDateTimeEpochMs),
        isFinished = project.isFinished,
        useLightTextColor = project.useLightTextColor,
        endDateTimeUtc = project.endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        projectTasks = projectTasks.map { it.toProjectSession() },
        isArchived = project.isArchived,
        trashedAt = project.trashedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        isPinned = project.isPinned,
        updatedAt = Instant.fromEpochMilliseconds(project.updatedAtEpochMs),
    )
}

fun ProjectTaskEntity.toProjectSession(): ProjectTask {
    return ProjectTask(
        projectTaskId = recordId,
        title = description,
        durationMillis = durationMillis,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        endDateTimeUtc = endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        isFinished = isFinished,
        parentProjectId = parentProjectId,
        isTimerRunning = isTimerRunning,
        updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMs),
    )
}

fun TaskWithIntervals.toProjectSession(): ProjectTask {
    return ProjectTask(
        projectTaskId = task.recordId,
        title = task.description,
        durationMillis = task.durationMillis,
        startDateTimeUtc = Instant.fromEpochMilliseconds(task.startDateTimeEpochMs),
        endDateTimeUtc = task.endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        isFinished = task.isFinished,
        parentProjectId = task.parentProjectId,
        isTimerRunning = task.isTimerRunning,
        intervals = intervals.map { it.toSessionInterval() },
        updatedAt = Instant.fromEpochMilliseconds(task.updatedAtEpochMs),
    )
}

fun ProjectTask.toProjectSessionEntity(): ProjectTaskEntity {
    return ProjectTaskEntity(
        recordId = projectTaskId,
        parentProjectId = parentProjectId,
        description = title,
        durationMillis = durationMillis ?: 0L,
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
        isFinished = isFinished,
        isTimerRunning = isTimerRunning,
        updatedAtEpochMs = updatedAt?.toEpochMilliseconds() ?: 0L,
    )
}

fun TaskIntervalEntity.toSessionInterval(): TaskInterval {
    return TaskInterval(
        intervalId = intervalId,
        parentSessionId = parentTaskId,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        endDateTimeUtc = endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        durationMillis = durationMillis
    )
}

fun TaskInterval.toSessionIntervalEntity(): TaskIntervalEntity {
    return TaskIntervalEntity(
        intervalId = intervalId,
        parentTaskId = parentSessionId,
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
        durationMillis = durationMillis
    )
}

fun Project.toCreateProjectRequest(): CreateProjectRequest {
    return CreateProjectRequest(
        id = projectId,
        title = title,
        description = description ?: "",
        color = colorArgb ?: 0,
        startDateTimeUtc = startDateTimeUtc.toString(),
        useLightTextColor = useLightTextColor,
        updatedAtUtc = updatedAt?.toString()
    )
}

fun Project.toUpdateProjectRequest(): UpdateProjectRequest {
    return UpdateProjectRequest(
        title = title,
        description = description,
        color = colorArgb,
        totalDuration = totalDurationMillis,
        startDateTimeUtc = startDateTimeUtc.toString(),
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeUtc?.toString(),
        trashedAtUtc = trashedAt?.toString(),
        pinned = isPinned,
        finished = isFinished,
        archived = isArchived,
        updatedAtUtc = updatedAt?.toString()
    )
}

fun ProjectTask.toCreateProjectTaskRequest(): CreateProjectTaskRequest {
    return CreateProjectTaskRequest(
        id = projectTaskId,
        description = title,
        durationMillis = durationMillis ?: 0,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        finished = isFinished,
        timerRunning = isTimerRunning,
    )
}

fun ProjectTask.toUpdateProjectTaskRequest(): UpdateProjectTaskRequest {
    return UpdateProjectTaskRequest(
        description = title,
        durationMillis = durationMillis ?: 0,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        finished = isFinished,
        timerRunning = isTimerRunning,
    )
}
