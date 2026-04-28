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