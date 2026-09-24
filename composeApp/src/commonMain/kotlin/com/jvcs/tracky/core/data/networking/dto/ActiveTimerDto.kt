package com.jvcs.tracky.core.data.networking.dto

import kotlinx.serialization.Serializable

/**
 * The wire shapes of `/api/timer/active`, verified against the deployed backend rather than read
 * off the spec (`Requirements/backend-active-timer-api.md`).
 *
 * Unlike the nested project payload, every row here already carries `parentProjectId`, so nothing
 * has to be handed down from an enclosing object.
 */
@Serializable
data class ActiveTimerDto(
    val intervalId: String,
    /** `task` or `sub_task`. */
    val kind: String,
    val parentProjectId: String,
    val parentTaskId: String,
    val parentSubTaskId: String? = null,
    val parentTaskIntervalId: String? = null,
    val startedAtUtc: String,
    val startedByDeviceId: String? = null,
    /** Present on the active timer itself as well as on the envelope. */
    val serverNowUtc: String? = null,
)

/**
 * One interval the call created, closed or modified.
 *
 * A single shape covers both levels, discriminated by [kind], with the parent ids that do not
 * apply left null. The server also stamps `changeSeq` here; it is deliberately not decoded,
 * because the client never stores a sequence per row — only the feed cursor is a sequence.
 */
@Serializable
data class TouchedIntervalDto(
    val kind: String,
    val id: String,
    val parentProjectId: String,
    val parentTaskId: String? = null,
    val parentSubTaskId: String? = null,
    val parentTaskIntervalId: String? = null,
    val startDateTimeUtc: String,
    val endDateTimeUtc: String? = null,
    val durationMillis: Long = 0L,
    val startedByDeviceId: String? = null,
    val updatedAtUtc: String? = null,
)

/** The `200` envelope shared by start and stop. */
@Serializable
data class ActiveTimerChangeDto(
    val active: ActiveTimerDto? = null,
    val touched: List<TouchedIntervalDto> = emptyList(),
    val serverNowUtc: String? = null,
)

/**
 * The `409` body.
 *
 * It carries the timer that is actually running - or null, when the refusal was "that interval is
 * already closed" - which is what lets a losing device converge without a second round trip.
 */
@Serializable
data class ActiveTimerConflictDto(
    val code: String? = null,
    val message: String? = null,
    val active: ActiveTimerDto? = null,
)

/** `PUT /api/timer/active`. */
@Serializable
data class StartActiveTimerRequest(
    val intervalId: String,
    val kind: String,
    val parentTaskId: String,
    val parentSubTaskId: String? = null,
    val parentTaskIntervalId: String? = null,
    val startedAtUtc: String,
    val deviceId: String,
)

/** `POST /api/timer/active/stop`. Names the interval, because the stop is a compare-and-swap. */
@Serializable
data class StopActiveTimerRequest(val intervalId: String, val endedAtUtc: String)
