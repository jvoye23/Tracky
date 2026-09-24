package com.jvcs.tracky.design_system.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
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
        assertThat(formatDuration(2.hours + 16.minutes + 9.seconds)).isEqualTo("02:16:09")
    }

    @Test
    fun secondsAreTruncatedNeverRounded() {
        // Rounding up would claim time that was never tracked.
        assertThat(formatDuration(999.milliseconds)).isEqualTo("00:00:00")
        assertThat(formatDuration(59.seconds + 999.milliseconds)).isEqualTo("00:00:59")
    }

    @Test
    fun hoursAreNotWrappedAtADay() {
        assertThat(formatDuration(75.hours + 21.minutes)).isEqualTo("75:21:00")
    }

    @Test
    fun aFormattedDurationParsesBackToItself() {
        val duration = 2.hours + 16.minutes + 9.seconds

        assertThat(parseDuration(formatDuration(duration))).isEqualTo(duration)
    }

    @Test
    fun theOldCentisecondFormatStillParses() {
        // Strings written by an earlier build, and anything still rendering four segments.
        assertThat(parseDuration("02:16:09:43")).isEqualTo(2.hours + 16.minutes + 9.seconds + 430.milliseconds)
    }

    @Test
    fun anUnparseableStringIsZeroRatherThanACrash() {
        assertThat(parseDuration("not a duration")).isEqualTo(Duration.ZERO)
        assertThat(parseDuration("")).isEqualTo(Duration.ZERO)
    }

    @Test
    fun aMalformedSegmentIsRejectedRatherThanTreatedAsZero() {
        // Unlike the strings above, these survive the segment count check and reach the parsing,
        // so they pin the guards themselves. The same damage has to give the same answer
        // wherever it sits - the fourth segment included.
        assertThat(parseDuration("aa:16:09")).isEqualTo(Duration.ZERO)
        assertThat(parseDuration("02:bb:09")).isEqualTo(Duration.ZERO)
        assertThat(parseDuration("02:16:cc")).isEqualTo(Duration.ZERO)
        assertThat(parseDuration("02:16:09:dd")).isEqualTo(Duration.ZERO)
    }
}
