@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project.data.mappers

import com.jvcs.tracky.core.data.networking.CreateSubTaskIntervalRequest
import com.jvcs.tracky.core.data.networking.UpdateSubTaskIntervalRequest
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun SubTaskIntervalEntity.toSubTaskInterval(): SubTaskInterval =
    SubTaskInterval(
        subTaskIntervalId = subTaskIntervalId,
        parentSubTaskId = parentSubTaskId,
        parentTaskIntervalId = parentTaskIntervalId,
        parentProjectId = parentProjectId,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        endDateTimeUtc = endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        durationMillis = durationMillis,
        startedParentTimer = startedParentTimer,
        startedByDeviceId = startedByDeviceId,
    )

fun SubTaskInterval.toSubTaskIntervalEntity(): SubTaskIntervalEntity =
    SubTaskIntervalEntity(
        subTaskIntervalId = subTaskIntervalId,
        parentSubTaskId = parentSubTaskId,
        parentTaskIntervalId = parentTaskIntervalId,
        parentProjectId = parentProjectId,
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
        durationMillis = durationMillis,
        startedParentTimer = startedParentTimer,
        startedByDeviceId = startedByDeviceId,
    )

// startedParentTimer is absent by design — which timer opened which is a purely local fact.
fun SubTaskInterval.toCreateSubTaskIntervalRequest(): CreateSubTaskIntervalRequest =
    CreateSubTaskIntervalRequest(
        id = subTaskIntervalId,
        parentTaskIntervalId = parentTaskIntervalId,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        durationMillis = durationMillis,
    )

fun SubTaskInterval.toUpdateSubTaskIntervalRequest(): UpdateSubTaskIntervalRequest =
    UpdateSubTaskIntervalRequest(
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        durationMillis = durationMillis,
    )
