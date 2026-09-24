package com.jvcs.tracky.designsystem.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val HMS_PARTS = 3
private const val HMS_CENTIS_PARTS = 4
private const val MILLIS_PER_CENTISECOND = 10

/**
 * "HH:mm:ss", e.g. `02:16:09`. Hours are not wrapped at a day - a timer left running over a
 * weekend has to read as the 75 hours it was.
 *
 * Seconds are truncated, not rounded: rounding 00:59:59.9 up would claim a second that was never
 * tracked. Whole seconds rather than centiseconds because the notification cannot redraw a hundred
 * times a second, and the two clocks must show the same characters.
 */
fun formatDuration(duration: Duration): String =
    duration.toComponents { hours, minutes, seconds, _ ->
        "${hours.toString().padStart(2, '0')}:" +
            "${minutes.toString().padStart(2, '0')}:" +
            seconds.toString().padStart(2, '0')
    }

/**
 * The inverse of [formatDuration]. Accepts the four-segment "HH:mm:ss:cc" form too, so strings
 * rendered by an earlier build still read back at the precision they were written with.
 *
 * Anything else is [Duration.ZERO] rather than an exception - this parses display text, and a
 * malformed string should not take a screen down.
 */
fun parseDuration(timeString: String): Duration {
    val parts = timeString.split(":")
    if (parts.size != HMS_PARTS && parts.size != HMS_CENTIS_PARTS) return Duration.ZERO

    val hours = parts[0].toLongOrNull() ?: return Duration.ZERO
    val minutes = parts[1].toLongOrNull() ?: return Duration.ZERO
    val seconds = parts[2].toLongOrNull() ?: return Duration.ZERO
    val centiseconds =
        if (parts.size == 4) {
            parts[3].toLongOrNull() ?: return Duration.ZERO
        } else {
            0L
        }

    return hours.hours + minutes.minutes + seconds.seconds + (centiseconds * MILLIS_PER_CENTISECOND).milliseconds
}

/**
 * "HH:mm", e.g. `01:00` — a coarse read of a duration. Seconds are truncated, not rounded:
 * rounding 00:59:30 up to 01:00 would claim a full hour that was never tracked.
 */
fun formatDurationHoursMinutes(duration: Duration): String =
    duration.toComponents { hours, minutes, _, _ ->
        "${hours.toString().padStart(2, '0')}:" +
            minutes.toString().padStart(2, '0')
    }

/** "HH:mm:ss", e.g. `00:04:02` — [formatDuration] without the centiseconds. */
fun formatDurationHoursMinutesSeconds(duration: Duration): String =
    duration.toComponents { hours, minutes, seconds, _ ->
        "${hours.toString().padStart(2, '0')}:" +
            "${minutes.toString().padStart(2, '0')}:" +
            seconds.toString().padStart(2, '0')
    }
