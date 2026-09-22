package com.jvcs.tracky.core.domain.timer

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import kotlin.time.Instant

/**
 * The server's view of the running timer.
 *
 * Read-and-arbitrate only: nothing here writes to Room, and nothing here decides what the user
 * sees. The repository above it stays offline-first — the local row is still what the UI reads.
 */
interface RemoteActiveTimerDataSource {

    /**
     * What the server currently has open, or null when nothing is running.
     *
     * Null is an answer, not an absence: the server replies `204` to say "nothing is running", and
     * that is exactly as authoritative as naming an interval.
     */
    suspend fun getActive(): Result<ActiveTimer?, DataError.Remote>

    /**
     * Makes [request] the running timer, closing whatever else was running at this one's start.
     *
     * Safe to retry: repeating the interval that is already active changes nothing.
     */
    suspend fun start(request: StartActiveTimer): Result<ActiveTimerChange, DataError.Remote>

    /**
     * Closes [intervalId] at [endedAt], but only if it is still the running timer.
     *
     * A compare-and-swap rather than "stop whatever is running", because those race: a stop sent
     * from a tablet while the user looked at a timer the phone replaced 200ms earlier would
     * otherwise kill the phone's fresh timer instead of the one the tablet meant.
     */
    suspend fun stop(intervalId: String, endedAt: Instant): Result<ActiveTimerChange, DataError.Remote>
}
