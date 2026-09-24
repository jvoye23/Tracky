package com.jvcs.tracky.features.project.data.interval

import com.jvcs.tracky.core.domain.sync.PendingSyncDataSource
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.sync.SyncOutcome
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.sync.drain
import com.jvcs.tracky.core.domain.sync.pushQueuedRow
import com.jvcs.tracky.core.domain.sync.toSyncOutcome
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.asEmptyDataResult
import com.jvcs.tracky.core.domain.util.getOrDefault
import com.jvcs.tracky.core.domain.util.isMissingOrForbidden
import com.jvcs.tracky.core.domain.util.isTransient
import com.jvcs.tracky.features.project.domain.interval.IntervalRepository
import com.jvcs.tracky.features.project.domain.interval.LocalIntervalDataSource
import com.jvcs.tracky.features.project.domain.interval.RemoteIntervalDataSource
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.task.LocalTaskDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Intervals are the last link in the sync chain: their route is nested under a task, which is in
 * turn nested under a project. Nothing here can be pushed until both parents exist server-side, so
 * every write first asks the queue whether the parent task is still local-only.
 *
 * [localTaskDataSource] is here for exactly two reads — the parent-pending check, and resolving the
 * project id of a queued DELETE, whose interval row is already gone by the time it drains.
 */
class OfflineFirstIntervalRepository(
    private val localIntervalDataSource: LocalIntervalDataSource,
    private val remoteIntervalDataSource: RemoteIntervalDataSource,
    private val localTaskDataSource: LocalTaskDataSource,
    private val pendingSyncDataSource: PendingSyncDataSource,
    private val syncScheduler: SyncScheduler,
    private val applicationScope: CoroutineScope,
    private val timeProvider: TimeProvider,
) : IntervalRepository {

    // CREATE/UPDATE interval: local first (optimistic), then remote — same flow as projects/tasks.
    override suspend fun createTaskInterval(interval: TaskInterval): EmptyResult<DataError> =
        writeThenPush(interval, isCreate = true)

    override suspend fun updateTaskInterval(interval: TaskInterval): EmptyResult<DataError> =
        writeThenPush(interval, isCreate = false)

    private suspend fun writeThenPush(interval: TaskInterval, isCreate: Boolean): EmptyResult<DataError> {
        // The timer has usually written this row already; the upsert is idempotent, and doing it
        // here keeps every entry point offline-first without the callers having to know that.
        val localResult = localIntervalDataSource.upsertTaskInterval(interval)
        if (localResult !is Result.Success) return localResult.asEmptyDataResult()

        return pushInterval(interval, isCreate)
    }

    override suspend fun getOpenIntervalByTaskId(taskId: String): Result<TaskInterval?, DataError> =
        localIntervalDataSource.getOpenIntervalByTaskId(taskId)

    // DELETE interval: local first, then remote, with the same offline-create-then-delete (ghost)
    // handling as tasks — an interval that never reached the server just drops out of the queue.
    override suspend fun deleteTaskInterval(intervalId: String): EmptyResult<DataError> {
        val interval =
            localIntervalDataSource
                .getIntervalById(intervalId)
                .getOrDefault(null) ?: return Result.Success(Unit) // already gone locally
        val hadPendingCreate = pendingSyncDataSource.hasPendingCreate(intervalId).getOrDefault(false)

        val localResult = localIntervalDataSource.deleteTaskInterval(intervalId)
        if (localResult !is Result.Success) return localResult.asEmptyDataResult()

        if (hadPendingCreate) {
            // Created offline and deleted before it ever reached the server → just drop the queue.
            pendingSyncDataSource.deleteOperationsByEntityId(intervalId)
            return Result.Success(Unit)
        }

        val taskId = interval.parentTaskId
        val remoteResult =
            applicationScope
                .async {
                    remoteIntervalDataSource.deleteInterval(interval.parentProjectId, taskId, intervalId)
                }.await()
        return when (remoteResult) {
            is Result.Success -> {
                Result.Success(Unit)
            }

            is Result.Error -> {
                when {
                    remoteResult.error.isTransient() -> {
                        // Local delete already succeeded; only surface an error if queuing the sync fails.
                        val queued = enqueueIntervalOperation(intervalId, taskId, PendingSyncOperation.OP_DELETE)
                        if (queued is Result.Success) scheduleSync()
                        queued
                    }

                    // Server already has no such interval — the delete is effectively done.
                    remoteResult.error.isMissingOrForbidden() -> {
                        Result.Success(Unit)
                    }

                    else -> {
                        remoteResult.asEmptyDataResult()
                    }
                }
            }
        }
    }

    /**
     * Pushes one interval, queueing it whenever the push cannot succeed yet.
     *
     * Three cases never reach the network, or come back from it, as an ordinary failure:
     * - The parent task is still queued for creation. There is no route to POST to yet, so the
     *   interval is queued straight away rather than spending a request to learn that.
     * - `CONFLICT` on a create means the POST already landed and only its response was lost, so the
     *   same interval is retried as an update rather than resolved by last-write-wins.
     * - A miss means the parent task does not exist server-side after all — the backstop for a
     *   task that was pushed and then deleted, or a queue row that went missing. That must be
     *   queued rather than dropped: the drain runs tasks before intervals, so the retry then
     *   succeeds. Dropping here would silently lose tracked time in exactly the offline case this
     *   feature exists for.
     */
    private suspend fun pushInterval(interval: TaskInterval, isCreate: Boolean): EmptyResult<DataError> {
        val taskId = interval.parentTaskId
        val operation = if (isCreate) PendingSyncOperation.OP_CREATE else PendingSyncOperation.OP_UPDATE

        if (pendingSyncDataSource.hasPendingCreate(taskId).getOrDefault(false)) {
            return queueForLater(interval.intervalId, taskId, operation)
        }

        val remoteResult =
            if (isCreate) {
                remoteIntervalDataSource.postInterval(interval)
            } else {
                remoteIntervalDataSource.updateInterval(interval)
            }

        return when (remoteResult) {
            // Server is canonical on the happy path, exactly like projects and tasks.
            is Result.Success -> {
                localIntervalDataSource.upsertTaskInterval(remoteResult.data).asEmptyDataResult()
            }

            is Result.Error -> {
                when {
                    isCreate && remoteResult.error == DataError.Remote.CONFLICT -> {
                        resolveIntervalConflict(interval)
                    }

                    remoteResult.error.isMissingOrForbidden() || remoteResult.error.isTransient() -> {
                        queueForLater(interval.intervalId, taskId, operation)
                    }

                    // Permanent error — the local row stands, nothing left to try.
                    else -> {
                        remoteResult.asEmptyDataResult()
                    }
                }
            }
        }
    }

    /** A duplicate create means the row is already on the server: push local state as an update. */
    private suspend fun resolveIntervalConflict(interval: TaskInterval): EmptyResult<DataError> =
        when (val updated = remoteIntervalDataSource.updateInterval(interval)) {
            is Result.Success -> {
                localIntervalDataSource.upsertTaskInterval(updated.data).asEmptyDataResult()
            }

            is Result.Error -> {
                if (updated.error.isTransient()) {
                    queueForLater(interval.intervalId, interval.parentTaskId, PendingSyncOperation.OP_UPDATE)
                } else {
                    updated.asEmptyDataResult()
                }
            }
        }

    // ---------------------------------------------------------------------------------------------
    // Pending-sync queue draining
    // ---------------------------------------------------------------------------------------------

    override suspend fun syncPendingIntervals(): EmptyResult<DataError> =
        pendingSyncDataSource.drain(matching = {
            it.entityType == PendingSyncOperation.ENTITY_INTERVAL
        }) { runIntervalOperation(it) }

    private suspend fun runIntervalOperation(op: PendingSyncOperation): SyncOutcome =
        when (op.operationType) {
            PendingSyncOperation.OP_CREATE, PendingSyncOperation.OP_UPDATE -> {
                localIntervalDataSource.getIntervalById(op.entityId).pushQueuedRow { pushInterval(op, it) }
            }

            PendingSyncOperation.OP_DELETE -> {
                deleteRemoteInterval(op)
            }

            else -> {
                SyncOutcome.DROP
            }
        }

    private suspend fun pushInterval(op: PendingSyncOperation, interval: TaskInterval): SyncOutcome {
        // The task drain runs before this one, so a still-pending CREATE means that push
        // failed too. Stay queued rather than burning a request that cannot succeed.
        if (pendingSyncDataSource.hasPendingCreate(interval.parentTaskId).getOrDefault(false)) {
            return SyncOutcome.RETRY
        }
        val result =
            if (op.operationType == PendingSyncOperation.OP_CREATE) {
                remoteIntervalDataSource.postInterval(interval)
            } else {
                remoteIntervalDataSource.updateInterval(interval)
            }
        return result.toSyncOutcome(
            onSuccess = { localIntervalDataSource.upsertTaskInterval(it) },
            onConflict = { resolveIntervalConflict(interval) },
        )
    }

    private suspend fun deleteRemoteInterval(op: PendingSyncOperation): SyncOutcome {
        val taskId = op.parentEntityId ?: return SyncOutcome.DROP
        if (pendingSyncDataSource.hasPendingCreate(taskId).getOrDefault(false)) {
            return SyncOutcome.RETRY
        }
        return parentProjectIdOf(taskId)
            ?.let { projectId ->
                remoteIntervalDataSource.deleteInterval(projectId, taskId, op.entityId).toSyncOutcome()
            }
            ?: SyncOutcome.DROP
    }

    /**
     * Resolves an interval route's project id from the task alone.
     *
     * Intervals carry their own `parentProjectId`, so this is only needed for a queued DELETE: by
     * the time that op drains, the local interval row is gone and the task id stored on the queue
     * entry is all that is left to go on.
     */
    private suspend fun parentProjectIdOf(taskId: String): String? =
        localTaskDataSource.getTaskById(taskId).getOrDefault(null)?.parentProjectId

    // ---------------------------------------------------------------------------------------------
    // Queue helpers
    // ---------------------------------------------------------------------------------------------

    /** Queues the op and wakes the scheduler. The local row already stands, so this is the success path. */
    private suspend fun queueForLater(
        intervalId: String,
        taskId: String,
        operationType: String,
    ): EmptyResult<DataError> {
        val queued = enqueueIntervalOperation(intervalId, taskId, operationType)
        if (queued is Result.Success) scheduleSync()
        return queued
    }

    // parentEntityId always carries the parent *task* id here, not the project id: unlike a task
    // DELETE, the interval routes need both ids on every operation, and the project is looked up
    // from the task when the op drains.
    private suspend fun enqueueIntervalOperation(
        intervalId: String,
        taskId: String,
        operationType: String,
    ): EmptyResult<DataError> =
        pendingSyncDataSource.enqueue(
            entityId = intervalId,
            entityType = PendingSyncOperation.ENTITY_INTERVAL,
            operationType = operationType,
            parentEntityId = taskId,
            createdAt = timeProvider.nowInstant,
        )

    private suspend fun scheduleSync() {
        applicationScope.launch { syncScheduler.schedulePeriodicSync() }.join()
    }
}
