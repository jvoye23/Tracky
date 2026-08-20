@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project.data.mappers

import com.jvcs.tracky.core.data.networking.CreateProjectRequest
import com.jvcs.tracky.core.data.networking.CreateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.CreateTaskIntervalRequest
import com.jvcs.tracky.core.data.networking.UpdateProjectRequest
import com.jvcs.tracky.core.data.networking.UpdateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.UpdateTaskIntervalRequest
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTasksEntity
import com.jvcs.tracky.core.database.relation.SubTaskWithIntervals
import com.jvcs.tracky.core.database.relation.TaskWithIntervals
import com.jvcs.tracky.core.database.relation.TaskWithSubTasks
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
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
        updatedAtEpochMs = ownUpdatedAt?.toEpochMilliseconds(),
        sortIndex = sortIndex,
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
        ownUpdatedAt = updatedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        sortIndex = sortIndex,
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
        projectTasks = projectTasks.map { it.toProjectTask() },
        isArchived = project.isArchived,
        trashedAt = project.trashedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        isPinned = project.isPinned,
        ownUpdatedAt = project.updatedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        sortIndex = project.sortIndex,
    )
}

fun ProjectTaskEntity.toProjectTask(): ProjectTask {
    return ProjectTask(
        projectTaskId = recordId,
        title = description,
        durationMillis = durationMillis,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        endDateTimeUtc = endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        isFinished = isFinished,
        parentProjectId = parentProjectId,
        isTimerRunning = isTimerRunning,
        ownUpdatedAt = updatedAtEpochMs?.let(Instant::fromEpochMilliseconds),
    )
}

fun TaskWithIntervals.toProjectTask(): ProjectTask {
    return ProjectTask(
        projectTaskId = task.recordId,
        title = task.description,
        durationMillis = task.durationMillis,
        startDateTimeUtc = Instant.fromEpochMilliseconds(task.startDateTimeEpochMs),
        endDateTimeUtc = task.endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        isFinished = task.isFinished,
        parentProjectId = task.parentProjectId,
        isTimerRunning = task.isTimerRunning,
        intervals = intervals.map { it.toTaskInterval() },
        ownUpdatedAt = task.updatedAtEpochMs?.let(Instant::fromEpochMilliseconds),
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
        updatedAtEpochMs = ownUpdatedAt?.toEpochMilliseconds(),
    )
}

fun TaskIntervalEntity.toTaskInterval(): TaskInterval {
    return TaskInterval(
        intervalId = intervalId,
        parentTaskId = parentTaskId,
        parentProjectId = parentProjectId,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        endDateTimeUtc = endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        durationMillis = durationMillis
    )
}

fun TaskInterval.toTaskIntervalEntity(): TaskIntervalEntity {
    return TaskIntervalEntity(
        intervalId = intervalId,
        parentTaskId = parentTaskId,
        parentProjectId = parentProjectId,
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
        updatedAtUtc = ownUpdatedAt?.toString(),
        sortIndex = sortIndex,
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
        isPinned = isPinned,
        isFinished = isFinished,
        isArchived = isArchived,
        updatedAtUtc = ownUpdatedAt?.toString(),
        sortIndex = sortIndex,
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

fun TaskInterval.toCreateTaskIntervalRequest(): CreateTaskIntervalRequest {
    return CreateTaskIntervalRequest(
        id = intervalId,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        durationMillis = durationMillis,
    )
}

fun TaskInterval.toUpdateTaskIntervalRequest(): UpdateTaskIntervalRequest {
    return UpdateTaskIntervalRequest(
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        durationMillis = durationMillis,
    )
}

fun ProjectSubTaskEntity.toProjectSubTask(): ProjectSubTask {
    return ProjectSubTask(
        projectSubTaskId = projectSubTaskId,
        parentProjectTaskId = parentProjectTaskId,
        parentProjectId = parentProjectId,
        title = title,
        description = description,
        durationMillis = durationMillis,
        isTimerRunning = isTimerRunning,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        endDateTimeUtc = endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        isFinished = isFinished,
        ownUpdatedAt = updatedAtEpochMs?.let(Instant::fromEpochMilliseconds),
    )
}

fun SubTaskWithIntervals.toProjectSubTask(): ProjectSubTask =
    subTask.toProjectSubTask().copy(subTaskIntervals = intervals.map { it.toSubTaskInterval() })

fun ProjectSubTask.toProjectSubTaskEntity(): ProjectSubTaskEntity {
    return ProjectSubTaskEntity(
        projectSubTaskId = projectSubTaskId,
        parentProjectTaskId = parentProjectTaskId,
        parentProjectId = parentProjectId,
        title = title,
        description = description,
        durationMillis = durationMillis,
        isTimerRunning = isTimerRunning,
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
        isFinished = isFinished,
        updatedAtEpochMs = ownUpdatedAt?.toEpochMilliseconds(),
    )
}

fun SubTaskIntervalEntity.toSubTaskInterval(): SubTaskInterval {
    return SubTaskInterval(
        subTaskIntervalId = subTaskIntervalId,
        parentSubTaskId = parentSubTaskId,
        parentTaskIntervalId = parentTaskIntervalId,
        parentProjectId = parentProjectId,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        endDateTimeUtc = endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        durationMillis = durationMillis
    )
}

fun SubTaskInterval.toSubTaskIntervalEntity(): SubTaskIntervalEntity {
    return SubTaskIntervalEntity(
        subTaskIntervalId = subTaskIntervalId,
        parentSubTaskId = parentSubTaskId,
        parentTaskIntervalId = parentTaskIntervalId,
        parentProjectId = parentProjectId,
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
        durationMillis = durationMillis
    )
}

/** The task's whole subtree: its own intervals plus its subtasks, each with theirs. */
fun TaskWithSubTasks.toProjectTask(): ProjectTask {
    return ProjectTask(
        projectTaskId = task.recordId,
        title = task.description,
        durationMillis = task.durationMillis,
        startDateTimeUtc = Instant.fromEpochMilliseconds(task.startDateTimeEpochMs),
        endDateTimeUtc = task.endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        isFinished = task.isFinished,
        parentProjectId = task.parentProjectId,
        isTimerRunning = task.isTimerRunning,
        intervals = intervals.map { it.toTaskInterval() },
        ownUpdatedAt = task.updatedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        subTasks = subTasks.map { it.toProjectSubTask() },
    )
}
