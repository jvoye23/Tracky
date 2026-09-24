@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project.data.mappers

import com.jvcs.tracky.core.data.networking.CreateSubTaskRequest
import com.jvcs.tracky.core.data.networking.UpdateSubTaskRequest
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.relation.SubTaskWithIntervals
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun ProjectSubTaskEntity.toProjectSubTask(): ProjectSubTask =
    ProjectSubTask(
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
        sortIndex = sortIndex,
    )

fun SubTaskWithIntervals.toProjectSubTask(): ProjectSubTask =
    subTask.toProjectSubTask().copy(subTaskIntervals = intervals.map { it.toSubTaskInterval() })

fun ProjectSubTask.toProjectSubTaskEntity(): ProjectSubTaskEntity =
    ProjectSubTaskEntity(
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
        sortIndex = sortIndex,
    )

// ---- Subtask request bodies -------------------------------------------------------------------
// The parent project and task are both in the route, so neither appears in a body.

fun ProjectSubTask.toCreateSubTaskRequest(): CreateSubTaskRequest =
    CreateSubTaskRequest(
        id = projectSubTaskId,
        title = title,
        description = description,
        durationMillis = durationMillis,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        isFinished = isFinished,
        isTimerRunning = isTimerRunning,
        sortIndex = sortIndex,
    )

fun ProjectSubTask.toUpdateSubTaskRequest(): UpdateSubTaskRequest =
    UpdateSubTaskRequest(
        title = title,
        description = description,
        durationMillis = durationMillis,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        isFinished = isFinished,
        isTimerRunning = isTimerRunning,
        sortIndex = sortIndex,
    )
