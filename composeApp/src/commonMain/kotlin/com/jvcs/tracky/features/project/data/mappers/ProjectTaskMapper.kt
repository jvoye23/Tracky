@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project.data.mappers

import com.jvcs.tracky.core.data.networking.CreateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.UpdateProjectTaskRequest
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.relation.TaskWithIntervals
import com.jvcs.tracky.core.database.relation.TaskWithSubTasks
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.task.sortedBySubTaskOrder
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun ProjectTaskEntity.toProjectTask(): ProjectTask =
    ProjectTask(
        projectTaskId = projectTaskId,
        title = title,
        description = description,
        durationMillis = durationMillis,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        endDateTimeUtc = endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        isFinished = isFinished,
        parentProjectId = parentProjectId,
        isTimerRunning = isTimerRunning,
        ownUpdatedAt = updatedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        sortIndex = sortIndex,
    )

fun TaskWithIntervals.toProjectTask(): ProjectTask =
    ProjectTask(
        projectTaskId = task.projectTaskId,
        title = task.title,
        description = task.description,
        durationMillis = task.durationMillis,
        startDateTimeUtc = Instant.fromEpochMilliseconds(task.startDateTimeEpochMs),
        endDateTimeUtc = task.endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        isFinished = task.isFinished,
        parentProjectId = task.parentProjectId,
        isTimerRunning = task.isTimerRunning,
        intervals = intervals.map { it.toTaskInterval() },
        ownUpdatedAt = task.updatedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        sortIndex = task.sortIndex,
    )

fun ProjectTask.toProjectTaskEntity(): ProjectTaskEntity =
    ProjectTaskEntity(
        projectTaskId = projectTaskId,
        parentProjectId = parentProjectId,
        title = title,
        description = description,
        durationMillis = durationMillis ?: 0L,
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
        isFinished = isFinished,
        isTimerRunning = isTimerRunning,
        updatedAtEpochMs = ownUpdatedAt?.toEpochMilliseconds(),
        sortIndex = sortIndex,
    )

fun ProjectTask.toCreateProjectTaskRequest(): CreateProjectTaskRequest =
    CreateProjectTaskRequest(
        id = projectTaskId,
        title = title,
        description = description,
        durationMillis = durationMillis ?: 0,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        isFinished = isFinished,
        isTimerRunning = isTimerRunning,
        sortIndex = sortIndex,
    )

fun ProjectTask.toUpdateProjectTaskRequest(): UpdateProjectTaskRequest =
    UpdateProjectTaskRequest(
        title = title,
        description = description,
        durationMillis = durationMillis ?: 0,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        isFinished = isFinished,
        isTimerRunning = isTimerRunning,
        sortIndex = sortIndex,
    )

/** The task's whole subtree: its own intervals plus its subtasks, each with theirs. */
fun TaskWithSubTasks.toProjectTask(): ProjectTask =
    ProjectTask(
        projectTaskId = task.projectTaskId,
        title = task.title,
        description = task.description,
        durationMillis = task.durationMillis,
        startDateTimeUtc = Instant.fromEpochMilliseconds(task.startDateTimeEpochMs),
        endDateTimeUtc = task.endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        isFinished = task.isFinished,
        parentProjectId = task.parentProjectId,
        isTimerRunning = task.isTimerRunning,
        intervals = intervals.map { it.toTaskInterval() },
        ownUpdatedAt = task.updatedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        subTasks = subTasks.map { it.toProjectSubTask() }.sortedBySubTaskOrder(),
        sortIndex = task.sortIndex,
    )
