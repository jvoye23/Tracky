@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project.data.mappers

import com.jvcs.tracky.core.data.networking.CreateTaskIntervalRequest
import com.jvcs.tracky.core.data.networking.UpdateTaskIntervalRequest
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun TaskIntervalEntity.toTaskInterval(): TaskInterval =
    TaskInterval(
        intervalId = intervalId,
        parentTaskId = parentTaskId,
        parentProjectId = parentProjectId,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        endDateTimeUtc = endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        durationMillis = durationMillis,
        startedByDeviceId = startedByDeviceId,
    )

fun TaskInterval.toTaskIntervalEntity(): TaskIntervalEntity =
    TaskIntervalEntity(
        intervalId = intervalId,
        parentTaskId = parentTaskId,
        parentProjectId = parentProjectId,
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
        durationMillis = durationMillis,
        startedByDeviceId = startedByDeviceId,
    )

fun TaskInterval.toCreateTaskIntervalRequest(): CreateTaskIntervalRequest =
    CreateTaskIntervalRequest(
        id = intervalId,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        durationMillis = durationMillis,
    )

fun TaskInterval.toUpdateTaskIntervalRequest(): UpdateTaskIntervalRequest =
    UpdateTaskIntervalRequest(
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        durationMillis = durationMillis,
    )
