package com.jvcs.tracky.design_system.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun formatDuration(duration: Duration): String {
    return duration.toComponents { hours, minutes, seconds, nanoseconds ->
        val centiseconds = nanoseconds / 10_000_000
        "${hours.toString().padStart(2, '0')}:" +
                "${minutes.toString().padStart(2, '0')}:" +
                "${seconds.toString().padStart(2, '0')}:" +
                "${centiseconds.toString().padStart(2, '0')}"
    }
}

fun parseDuration(timeString: String): Duration {
    // 1. Split the string by ":"
    // Format is "HH:mm:ss:cc" (centiseconds)
    val parts = timeString.split(":")

    // Safety check for format
    if (parts.size != 4) return Duration.ZERO

    val hours = parts[0].toLongOrNull() ?: 0L
    val minutes = parts[1].toLongOrNull() ?: 0L
    val seconds = parts[2].toLongOrNull() ?: 0L
    val centiseconds = parts[3].toLongOrNull() ?: 0L

    // 2. Sum up the components
    // Note: 1 centisecond = 10 milliseconds
    return hours.hours +
            minutes.minutes +
            seconds.seconds +
            (centiseconds * 10).milliseconds
}

/**
 * "HH:mm", e.g. `01:00` — a coarse read of a duration. Seconds are truncated, not rounded:
 * rounding 00:59:30 up to 01:00 would claim a full hour that was never tracked.
 */
fun formatDurationHoursMinutes(duration: Duration): String {
    return duration.toComponents { hours, minutes, _, _ ->
        "${hours.toString().padStart(2, '0')}:" +
                minutes.toString().padStart(2, '0')
    }
}

/** "HH:mm:ss", e.g. `00:04:02` — [formatDuration] without the centiseconds. */
fun formatDurationHoursMinutesSeconds(duration: Duration): String {
    return duration.toComponents { hours, minutes, seconds, _ ->
        "${hours.toString().padStart(2, '0')}:" +
                "${minutes.toString().padStart(2, '0')}:" +
                seconds.toString().padStart(2, '0')
    }
}