package com.jvcs.tracky.core.data.networking.mappers

import com.jvcs.tracky.core.data.networking.StartActiveTimerRequest
import com.jvcs.tracky.core.data.networking.dto.ActiveTimerChangeDto
import com.jvcs.tracky.core.data.networking.dto.ActiveTimerConflictDto
import com.jvcs.tracky.core.data.networking.dto.ActiveTimerDto
import com.jvcs.tracky.core.data.networking.dto.TouchedIntervalDto
import com.jvcs.tracky.core.domain.timer.ActiveTimer
import com.jvcs.tracky.core.domain.timer.ActiveTimerChange
import com.jvcs.tracky.core.domain.timer.ActiveTimerKind
import com.jvcs.tracky.core.domain.timer.StartActiveTimer
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlin.time.Instant

internal const val KIND_TASK = "task"
internal const val KIND_SUB_TASK = "sub_task"

fun ActiveTimerKind.toWire(): String =
    when (this) {
        ActiveTimerKind.TASK -> KIND_TASK
        ActiveTimerKind.SUB_TASK -> KIND_SUB_TASK
    }

/**
 * An unrecognised `kind` is read as a task rather than rejected.
 *
 * The wire only has two values and a subtask row is identifiable by its parent ids anyway, so
 * failing the whole response over an unknown discriminator would cost more than it protects.
 */
fun String.toActiveTimerKind(): ActiveTimerKind =
    if (this == KIND_SUB_TASK) ActiveTimerKind.SUB_TASK else ActiveTimerKind.TASK

fun ActiveTimerDto.toActiveTimer(): ActiveTimer =
    ActiveTimer(
        intervalId = intervalId,
        kind = kind.toActiveTimerKind(),
        parentProjectId = parentProjectId,
        parentTaskId = parentTaskId,
        parentSubTaskId = parentSubTaskId,
        parentTaskIntervalId = parentTaskIntervalId,
        startedAt = Instant.parse(startedAtUtc),
        startedByDeviceId = startedByDeviceId,
    )

fun StartActiveTimer.toRequest(): StartActiveTimerRequest =
    StartActiveTimerRequest(
        intervalId = intervalId,
        kind = kind.toWire(),
        parentTaskId = parentTaskId,
        parentSubTaskId = parentSubTaskId,
        parentTaskIntervalId = parentTaskIntervalId,
        startedAtUtc = startedAt.toString(),
        deviceId = deviceId,
    )

/**
 * Splits the touched rows by level, dropping any the client could not write anyway.
 *
 * A task interval needs its `parentTaskId` and a subtask interval both `parentSubTaskId` and
 * `parentTaskIntervalId`; those back NOT NULL columns and cascading foreign keys. A row missing
 * one is skipped rather than thrown on, for the same reason the delta feed drops a row with no
 * `parentProjectId`: one malformed entry should not cost the whole response, and the caller's
 * next pull will carry the row properly.
 */
fun ActiveTimerChangeDto.toActiveTimerChange(): ActiveTimerChange.Applied =
    ActiveTimerChange.Applied(
        active = active?.toActiveTimer(),
        touchedTaskIntervals = touched.filter { it.kind != KIND_SUB_TASK }.mapNotNull { it.toTaskInterval() },
        touchedSubTaskIntervals = touched.filter { it.kind == KIND_SUB_TASK }.mapNotNull { it.toSubTaskInterval() },
        serverNow = serverNowUtc?.toInstantOrNull(),
    )

fun ActiveTimerConflictDto.toRejected(): ActiveTimerChange.Rejected =
    ActiveTimerChange.Rejected(
        active = active?.toActiveTimer(),
        // The conflict body has no envelope timestamp, but the timer it names carries one.
        serverNow = active?.serverNowUtc?.toInstantOrNull(),
    )

private fun TouchedIntervalDto.toTaskInterval(): TaskInterval? {
    val taskId = parentTaskId ?: return null
    return TaskInterval(
        intervalId = id,
        parentTaskId = taskId,
        parentProjectId = parentProjectId,
        startDateTimeUtc = Instant.parse(startDateTimeUtc),
        endDateTimeUtc = endDateTimeUtc?.let(Instant::parse),
        durationMillis = durationMillis,
        startedByDeviceId = startedByDeviceId,
    )
}

private fun TouchedIntervalDto.toSubTaskInterval(): SubTaskInterval? {
    val subTaskId = parentSubTaskId ?: return null
    val taskIntervalId = parentTaskIntervalId ?: return null
    return SubTaskInterval(
        subTaskIntervalId = id,
        parentTaskIntervalId = taskIntervalId,
        parentSubTaskId = subTaskId,
        parentProjectId = parentProjectId,
        startDateTimeUtc = Instant.parse(startDateTimeUtc),
        endDateTimeUtc = endDateTimeUtc?.let(Instant::parse),
        durationMillis = durationMillis,
        // Purely local facts with no wire counterpart; the merge keeps whatever the local row
        // holds, exactly as it does for a pulled interval.
        startedParentTimer = false,
        startedByDeviceId = startedByDeviceId,
    )
}

private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()
