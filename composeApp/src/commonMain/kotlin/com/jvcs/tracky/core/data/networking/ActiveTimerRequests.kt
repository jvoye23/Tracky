package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

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
