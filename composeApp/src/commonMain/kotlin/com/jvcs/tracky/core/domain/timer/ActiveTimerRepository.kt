package com.jvcs.tracky.core.domain.timer

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.features.project.domain.models.TaskInterval

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
}
