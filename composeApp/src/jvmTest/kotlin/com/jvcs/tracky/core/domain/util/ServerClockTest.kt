package com.jvcs.tracky.core.domain.util

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class ServerClockTest {

    private val deviceNow = Instant.fromEpochMilliseconds(1_000_000)
    private val timeProvider = FakeTimeProvider().apply { now = deviceNow }

    @Test
    fun withNothingMeasuredItIsJustTheDeviceClock() = runTest {
        val clock = ServerClock(timeProvider, FakeServerClockOffsetStore())

        assertEquals(deviceNow, clock.now())
    }

    @Test
    fun aMeasuredOffsetIsApplied() = runTest {
        val clock = ServerClock(timeProvider, FakeServerClockOffsetStore())

        // The device is 40 seconds behind the server.
        clock.observe(serverNow = deviceNow + 40.seconds, receivedAt = deviceNow)

        assertEquals(deviceNow + 40.seconds, clock.now())
    }

    @Test
    fun aDeviceRunningFastIsCorrectedDownwards() = runTest {
        val clock = ServerClock(timeProvider, FakeServerClockOffsetStore())

        clock.observe(serverNow = deviceNow - 25.seconds, receivedAt = deviceNow)

        assertEquals(deviceNow - 25.seconds, clock.now())
    }

    @Test
    fun theOffsetIsPersisted() = runTest {
        val store = FakeServerClockOffsetStore()
        ServerClock(timeProvider, store).observe(deviceNow + 40.seconds, deviceNow)

        assertEquals(40_000L, store.offsetMillis())
    }

    @Test
    fun aColdStartUsesLastSessionsMeasurement() = runTest {
        // Two physical clocks do not drift meaningfully between launches, so the stored value is
        // a far better guess than assuming zero — and it works with no network at all.
        val clock = ServerClock(timeProvider, FakeServerClockOffsetStore(millis = 40_000))

        assertEquals(deviceNow + 40.seconds, clock.now())
    }

    @Test
    fun aLaterMeasurementReplacesAnEarlierOne() = runTest {
        val clock = ServerClock(timeProvider, FakeServerClockOffsetStore(millis = 40_000))

        clock.observe(serverNow = deviceNow + 5.seconds, receivedAt = deviceNow)

        // Not averaged: the newest sample is the best estimate of the current skew, and a user
        // who has just fixed their clock should not be corrected towards the old error.
        assertEquals(deviceNow + 5.seconds, clock.now())
    }

    @Test
    fun theClockFollowsTheDeviceBetweenMeasurements() = runTest {
        val clock = ServerClock(timeProvider, FakeServerClockOffsetStore(millis = 40_000))

        timeProvider.now = deviceNow + 10.seconds

        // The offset is a constant correction, not a frozen timestamp; the timer still ticks.
        assertEquals(deviceNow + 50.seconds, clock.now())
    }
}
