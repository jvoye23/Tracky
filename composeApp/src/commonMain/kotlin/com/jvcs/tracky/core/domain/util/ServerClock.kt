package com.jvcs.tracky.core.domain.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * The device clock, corrected by how far it is known to be from the server's.
 *
 * A running timer's elapsed value is `now - startedAt`, computed independently on every device.
 * When the interval was started on the user's *other* phone, `startedAt` came from that phone's
 * clock, so any disagreement between the two shows up directly as a wrong duration. Forty seconds
 * of skew is forty seconds of tracked time that does not exist.
 *
 * The fix is not to make the server own timestamps — the backend's contract is that the client
 * does, and that is the right contract, because the client owns the wall-clock meaning of its
 * records. It is to stop pretending two devices agree, by measuring the difference and applying
 * it where it matters.
 *
 * **Only the timer uses this.** A project's `updatedAt` being forty seconds out changes nothing;
 * a last-write-wins comparison between two devices is decided by minutes and hours, not seconds.
 * Everything else keeps using [TimeProvider] directly.
 */
class ServerClock(private val timeProvider: TimeProvider, private val offsetStore: ServerClockOffsetStore) {
    // Read once into memory: the tick reads this every second, and a DataStore round trip per
    // tick would be absurd. Seeded lazily on first read so a cold start with no network is still
    // corrected by whatever the last session measured.
    private var cachedOffset: Duration? = null

    /** Now, as the server would report it. */
    suspend fun now(): Instant = timeProvider.nowInstant + offset()

    /**
     * Records the server's clock as observed in a response.
     *
     * [serverNow] is the server's own time, [receivedAt] this device's time when the response
     * arrived. The round trip is not compensated for: half of it would be a better estimate, but
     * the caller does not measure it, and being wrong by a few hundred milliseconds does not
     * matter for a clock rendered to the second. Being wrong by a minute does, and that is what
     * this catches.
     */
    suspend fun observe(serverNow: Instant, receivedAt: Instant) {
        val measured = serverNow - receivedAt
        cachedOffset = measured
        offsetStore.setOffsetMillis(measured.inWholeMilliseconds)
    }

    private suspend fun offset(): Duration {
        cachedOffset?.let { return it }
        val stored = offsetStore.offsetMillis()?.milliseconds ?: Duration.ZERO
        cachedOffset = stored
        return stored
    }
}

/** Where the measured offset survives a process restart. */
interface ServerClockOffsetStore {
    suspend fun offsetMillis(): Long?

    suspend fun setOffsetMillis(millis: Long)
}
