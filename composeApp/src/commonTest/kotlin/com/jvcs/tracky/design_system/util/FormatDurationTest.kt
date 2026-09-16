package com.jvcs.tracky.design_system.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FormatDurationTest {

    @Test
    fun aDurationReadsAsWholeSeconds() {
        // The notification cannot redraw a hundred times a second, and the two clocks have to
        // show the same characters, so the app reads in seconds too.
        assertEquals("02:16:09", formatDuration(2.hours + 16.minutes + 9.seconds))
    }

    @Test
    fun secondsAreTruncatedNeverRounded() {
        // Rounding up would claim time that was never tracked.
        assertEquals("00:00:00", formatDuration(999.milliseconds))
        assertEquals("00:00:59", formatDuration(59.seconds + 999.milliseconds))
    }

    @Test
    fun hoursAreNotWrappedAtADay() {
        assertEquals("75:21:00", formatDuration(75.hours + 21.minutes))
    }

    @Test
    fun aFormattedDurationParsesBackToItself() {
        val duration = 2.hours + 16.minutes + 9.seconds

        assertEquals(duration, parseDuration(formatDuration(duration)))
    }

    @Test
    fun theOldCentisecondFormatStillParses() {
        // Strings written by an earlier build, and anything still rendering four segments.
        assertEquals(2.hours + 16.minutes + 9.seconds + 430.milliseconds, parseDuration("02:16:09:43"))
    }

    @Test
    fun anUnparseableStringIsZeroRatherThanACrash() {
        assertEquals(Duration.ZERO, parseDuration("not a duration"))
        assertEquals(Duration.ZERO, parseDuration(""))
    }

    @Test
    fun aMalformedSegmentIsRejectedRatherThanTreatedAsZero() {
        // Unlike the strings above, these survive the segment count check and reach the parsing,
        // so they pin the guards themselves. The same damage has to give the same answer
        // wherever it sits - the fourth segment included.
        assertEquals(Duration.ZERO, parseDuration("aa:16:09"))
        assertEquals(Duration.ZERO, parseDuration("02:bb:09"))
        assertEquals(Duration.ZERO, parseDuration("02:16:cc"))
        assertEquals(Duration.ZERO, parseDuration("02:16:09:dd"))
    }
}
