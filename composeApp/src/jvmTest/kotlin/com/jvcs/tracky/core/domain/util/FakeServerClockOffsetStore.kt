package com.jvcs.tracky.core.domain.util

/** An in-memory offset, so a test can seed one or assert the applier measured it. */
internal class FakeServerClockOffsetStore(
    private var millis: Long? = null
) : ServerClockOffsetStore {

    override suspend fun offsetMillis(): Long? = millis

    override suspend fun setOffsetMillis(millis: Long) {
        this.millis = millis
    }
}

/** A [ServerClock] over a pinned device clock, uncorrected unless a test seeds an offset. */
internal fun testServerClock(
    timeProvider: TimeProvider = FakeTimeProvider(),
    offsetMillis: Long? = null
) = ServerClock(timeProvider, FakeServerClockOffsetStore(offsetMillis))
