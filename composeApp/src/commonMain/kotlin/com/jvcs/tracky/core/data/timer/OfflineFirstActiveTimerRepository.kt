package com.jvcs.tracky.core.data.timer

import com.jvcs.tracky.core.domain.device.DeviceIdProvider
import com.jvcs.tracky.core.domain.sync.DeltaSyncApplier
import com.jvcs.tracky.core.domain.sync.PendingSyncDataSource
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.timer.ActiveTimerChange
import com.jvcs.tracky.core.domain.timer.ActiveTimerKind
import com.jvcs.tracky.core.domain.timer.ActiveTimerRepository
import com.jvcs.tracky.core.domain.timer.RemoteActiveTimerDataSource
import com.jvcs.tracky.core.domain.timer.StartActiveTimer
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.ServerClock
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.asEmptyDataResult
import com.jvcs.tracky.core.domain.util.isMissingOrForbidden
import com.jvcs.tracky.core.domain.util.isTransient
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.project.LocalServerTreeDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * Takes the timer's transitions to the server and writes back what it says happened.
 *
 * Three rules shape everything here.
 *
 * **It never banks a duration.** Closing an interval and adding its time to the task stays with the
 * caller, because only the caller knows whether this device owns the timer. The server's task row
 * already carries a foreign timer's time, so banking it here again would double-count it.
 *
 * **A refusal is not a failure.** The server saying "that is not the timer that is running" means
 * another device moved on, and the answer is to re-read the server's state — never to close the
 * local row on a guess about a timer that may still be running somewhere else.
 *
 * **Anything it cannot deliver goes on the existing interval queue**, rather than a new
 * `entityType` — those strings are persisted, and the drain already knows how to push an interval.
 * Offline this degrades to exactly the behaviour the app had before cross-device sync: the
 * takeover is lost, the tracked time is not.
 */
class OfflineFirstActiveTimerRepository(
    private val remoteActiveTimerDataSource: RemoteActiveTimerDataSource,
    private val localServerTreeDataSource: LocalServerTreeDataSource,
    private val deviceIdProvider: DeviceIdProvider,
    private val pendingSyncDataSource: PendingSyncDataSource,
    private val deltaSyncApplier: DeltaSyncApplier,
    private val syncScheduler: SyncScheduler,
    private val serverClock: ServerClock,
    private val timeProvider: TimeProvider,
    private val applicationScope: CoroutineScope,
) : ActiveTimerRepository {

    override suspend fun start(taskInterval: TaskInterval, subTaskInterval: SubTaskInterval?): EmptyResult<DataError> {
        val deviceId = deviceIdProvider.deviceId()
        // The inner row wins when there is one. Timing a subtask opens its parent task's interval
        // as well, but naming the task interval here would let another device stop the task while
        // this one still shows the subtask running — two devices disagreeing about what is timed.
        val request =
            if (subTaskInterval != null) {
                StartActiveTimer(
                    intervalId = subTaskInterval.subTaskIntervalId,
                    kind = ActiveTimerKind.SUB_TASK,
                    parentTaskId = taskInterval.parentTaskId,
                    parentSubTaskId = subTaskInterval.parentSubTaskId,
                    parentTaskIntervalId = taskInterval.intervalId,
                    startedAt = subTaskInterval.startDateTimeUtc,
                    deviceId = deviceId,
                )
            } else {
                StartActiveTimer(
                    intervalId = taskInterval.intervalId,
                    kind = ActiveTimerKind.TASK,
                    parentTaskId = taskInterval.parentTaskId,
                    parentSubTaskId = null,
                    parentTaskIntervalId = null,
                    startedAt = taskInterval.startDateTimeUtc,
                    deviceId = deviceId,
                )
            }

        val sentAt = timeProvider.nowInstant
        return when (val result = remoteActiveTimerDataSource.start(request)) {
            is Result.Success -> {
                settle(result.data, midpoint(sentAt, timeProvider.nowInstant))
            }

            is Result.Error -> {
                fallBackToTheQueue(
                    intervalId = request.intervalId,
                    kind = request.kind,
                    // A subtask interval hangs off its subtask, not the task, and the drain resolves
                    // the rest of the ancestry from local rows.
                    parentId = request.parentSubTaskId ?: request.parentTaskId,
                    operationType = PendingSyncOperation.OP_CREATE,
                    error = result.error,
                )
            }
        }
    }

    override suspend fun stop(
        intervalId: String,
        kind: ActiveTimerKind,
        endedAt: Instant,
    ): EmptyResult<DataError> = stopAt(intervalId, kind, endedAt, timeProvider.nowInstant)

    private suspend fun stopAt(
        intervalId: String,
        kind: ActiveTimerKind,
        endedAt: Instant,
        sentAt: Instant,
    ): EmptyResult<DataError> =
        when (val result = remoteActiveTimerDataSource.stop(intervalId, endedAt)) {
            is Result.Success -> {
                settle(result.data, midpoint(sentAt, timeProvider.nowInstant))
            }

            is Result.Error -> {
                fallBackToTheQueue(
                    intervalId = intervalId,
                    kind = kind,
                    // An UPDATE re-reads the row when it drains, so the stored parent goes unused.
                    parentId = null,
                    operationType = PendingSyncOperation.OP_UPDATE,
                    error = result.error,
                )
            }
        }

    /** The instant halfway between a request leaving and its answer arriving. */
    private fun midpoint(sentAt: Instant, receivedAt: Instant): Instant = sentAt + (receivedAt - sentAt) / 2

    /**
     * Writes back whatever the server says it did.
     *
     * On [ActiveTimerChange.Applied] that is the echoed rows, which is how the device that just
     * superseded another one's timer closes it here without waiting for the next pull.
     *
     * On [ActiveTimerChange.Rejected] there is nothing to write: the body names what is running,
     * but not the instant the interval this device asked about was closed — and inventing one is
     * the guess `StrandedTimerReconciler` exists to refuse. A pull carries the real row, so that is
     * what is asked for.
     */
    private suspend fun settle(change: ActiveTimerChange, receivedAt: Instant): EmptyResult<DataError> {
        // receivedAt, not "now": settle() runs after the round trip, so passing the current
        // instant would credit the whole call's latency to clock skew.
        change.serverNow?.let { serverClock.observe(it, receivedAt) }

        return when (change) {
            is ActiveTimerChange.Applied -> {
                localServerTreeDataSource
                    .applyTimerEcho(
                        taskIntervals = change.touchedTaskIntervals,
                        subTaskIntervals = change.touchedSubTaskIntervals,
                    ).asEmptyDataResult()
            }

            is ActiveTimerChange.Rejected -> {
                // Deliberately not surfaced as an error: the user's action was understood, it just
                // lost a race, and the local row is about to be corrected from the server.
                deltaSyncApplier.pullChanges()
                Result.Success(Unit)
            }
        }
    }

    /**
     * A miss is queued rather than dropped, exactly as the interval repository does: the drain
     * runs tasks before intervals, so a parent that has not been pushed yet makes the retry
     * succeed. A permanent error leaves the local row standing — nothing left to try, and the
     * timer the user is looking at is still this device's truth.
     */
    private suspend fun fallBackToTheQueue(
        intervalId: String,
        kind: ActiveTimerKind,
        parentId: String?,
        operationType: String,
        error: DataError.Remote,
    ): EmptyResult<DataError> {
        if (!error.isTransient() && !error.isMissingOrForbidden()) {
            return Result.Error(error)
        }
        val entityType =
            when (kind) {
                ActiveTimerKind.TASK -> PendingSyncOperation.ENTITY_INTERVAL
                ActiveTimerKind.SUB_TASK -> PendingSyncOperation.ENTITY_SUBTASK_INTERVAL
            }
        val queued =
            pendingSyncDataSource.enqueue(
                entityId = intervalId,
                entityType = entityType,
                operationType = operationType,
                parentEntityId = parentId,
                createdAt = timeProvider.nowInstant,
            )
        if (queued is Result.Success) {
            applicationScope.launch { syncScheduler.schedulePeriodicSync() }.join()
        }
        return queued.asEmptyDataResult()
    }
}
