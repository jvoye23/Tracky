package com.jvcs.tracky.core.domain.timer

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlin.time.Instant

/**
 * Runs the timer's transitions past the server, so that one timer runs per user rather than one
 * per device.
 *
 * Deliberately narrow. It does not open or close local rows and it never banks a duration onto a
 * task — the caller still owns the local write, because only the caller knows whether the timer
 * being stopped is this device's to bank. Banking a foreign stop would double-count it: the
 * server's task row already carries that time.
 *
 * Everything here degrades. When the server cannot be reached the change is queued on the existing
 * interval queue and the app behaves exactly as it did before cross-device sync — the only thing
 * lost while offline is the takeover, not the tracking.
 */
interface ActiveTimerRepository {

    /**
     * Announces a task interval this device has just opened as the one running timer, closing
     * whatever was running elsewhere at this one's start.
     *
     * Subtask timers follow in their own slice: timing a subtask opens two intervals at once, and
     * the inner one is what the server has to arbitrate.
     */
    suspend fun start(taskInterval: TaskInterval): EmptyResult<DataError>

    /**
     * Closes [intervalId] at [endedAt], but only while it is still the running timer.
     *
     * A compare-and-swap rather than "stop whatever is running", because those race: a stop sent
     * from a tablet while the user looked at a timer the phone replaced 200ms earlier would
     * otherwise kill the phone's fresh timer instead of the one the tablet meant.
     *
     * [kind] says which table the id lives in. The server does not need telling — it knows the
     * row — but a stop that has to be queued does: the outbox routes by entity type, and a subtask
     * interval queued as a task one would drain to the wrong endpoint.
     */
    suspend fun stop(
        intervalId: String,
        kind: ActiveTimerKind,
        endedAt: Instant
    ): EmptyResult<DataError>
}
