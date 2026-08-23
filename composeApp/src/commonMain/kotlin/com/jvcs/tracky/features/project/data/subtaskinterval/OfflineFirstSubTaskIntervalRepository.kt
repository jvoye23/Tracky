package com.jvcs.tracky.features.project.data.subtaskinterval

import com.jvcs.tracky.core.domain.sync.PendingSyncDataSource
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.sync.SyncOutcome
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.sync.toSyncOutcome
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.asEmptyDataResult
import com.jvcs.tracky.core.domain.util.getOrDefault
import com.jvcs.tracky.core.domain.util.isTransient
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.subtask.LocalSubTaskDataSource
import com.jvcs.tracky.features.project.domain.subtaskinterval.LocalSubTaskIntervalDataSource
import com.jvcs.tracky.features.project.domain.subtaskinterval.RemoteSubTaskIntervalDataSource
import com.jvcs.tracky.features.project.domain.subtaskinterval.SubTaskIntervalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Subtask intervals are the deepest row in the tree: their route is nested under a subtask, under a
 * task, under a project. Nothing here can be pushed until all three exist server-side, so every
 * write first asks the queue whether the parent subtask is still local-only.
 *
 * [localSubTaskDataSource] is what makes the route buildable at all. A SubTaskInterval carries its
 * project and subtask ids but never its *task* id, so every call resolves that from the parent
 * subtask row — including a queued DELETE, whose own row is already gone by the time it drains.
 */
class OfflineFirstSubTaskIntervalRepository(
    private val localSubTaskIntervalDataSource: LocalSubTaskIntervalDataSource,
    private val remoteSubTaskIntervalDataSource: RemoteSubTaskIntervalDataSource,
    private val localSubTaskDataSource: LocalSubTaskDataSource,
    private val pendingSyncDataSource: PendingSyncDataSource,
    private val syncScheduler: SyncScheduler,
    private val applicationScope: CoroutineScope,
    private val timeProvider: TimeProvider
) : SubTaskIntervalRepository {

    // CREATE/UPDATE interval: local first (optimistic), then remote — same flow as task intervals.
    override suspend fun createSubTaskInterval(interval: SubTaskInterval): EmptyResult<DataError> =
        writeThenPush(interval, isCreate = true)

    override suspend fun updateSubTaskInterval(interval: SubTaskInterval): EmptyResult<DataError> =
        writeThenPush(interval, isCreate = false)

    private suspend fun writeThenPush(
        interval: SubTaskInterval,
        isCreate: Boolean
    ): EmptyResult<DataError> {
        // The timer has usually written this row already; the upsert is idempotent, and doing it
        // here keeps every entry point offline-first without the callers having to know that.
        val localResult = localSubTaskIntervalDataSource.upsertSubTaskInterval(interval)
        if (localResult !is Result.Success) return localResult.asEmptyDataResult()

        return pushInterval(interval, isCreate)
    }

    override suspend fun getOpenIntervalBySubTaskId(
        subTaskId: String
    ): Result<SubTaskInterval?, DataError> =
        localSubTaskIntervalDataSource.getOpenIntervalBySubTaskId(subTaskId)

    // DELETE interval: local first, then remote, with the same offline-create-then-delete (ghost)
    // handling as task intervals — one that never reached the server just drops out of the queue.
    override suspend fun deleteSubTaskInterval(intervalId: String): EmptyResult<DataError> {
        val interval = localSubTaskIntervalDataSource.getSubTaskIntervalById(intervalId)
            .getOrDefault(null) ?: return Result.Success(Unit) // already gone locally
        val hadPendingCreate = pendingSyncDataSource.hasPendingCreate(intervalId).getOrDefault(false)

        val localResult = localSubTaskIntervalDataSource.deleteSubTaskInterval(intervalId)
        if (localResult !is Result.Success) return localResult.asEmptyDataResult()

        val subTaskId = interval.parentSubTaskId
        if (hadPendingCreate) {
            // Created offline and deleted before it ever reached the server → just drop the queue.
            pendingSyncDataSource.deleteOperationsByEntityId(intervalId)
            return Result.Success(Unit)
        }

        // The parent subtask was created offline too, so the server has neither it nor this
        // interval — and deleting the subtask later takes its intervals with it by cascade.
        if (pendingSyncDataSource.hasPendingCreate(subTaskId).getOrDefault(false)) {
            return Result.Success(Unit)
        }

        val taskId = parentTaskIdOf(subTaskId)
            ?: return Result.Success(Unit) // subtask already gone; the server cascade covered it
        val remoteResult = applicationScope.async {
            remoteSubTaskIntervalDataSource.deleteInterval(
                projectId = interval.parentProjectId,
                taskId = taskId,
                subTaskId = subTaskId,
                intervalId = intervalId
            )
        }.await()
        return when (remoteResult) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> when {
                remoteResult.error.isTransient() -> {
                    // Local delete already succeeded; only surface an error if queuing it fails.
                    queueForLater(intervalId, subTaskId, PendingSyncOperation.OP_DELETE)
                }
                // Server already has no such interval — the delete is effectively done.
                remoteResult.error == DataError.Remote.NOT_FOUND -> Result.Success(Unit)
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    /**
     * Pushes one subtask interval, queueing it whenever the push cannot succeed yet.
     *
     * The same three exceptions OfflineFirstIntervalRepository makes, one level down:
     * - The parent subtask is still queued for creation, so there is no route to POST to yet.
     * - `CONFLICT` on a create means the POST already landed and only its response was lost, so the
     *   same interval is retried as an update rather than resolved by last-write-wins. An interval
     *   carries no stamp of its own, so there would be nothing to compare.
     * - `NOT_FOUND` means the parent subtask does not exist server-side after all. That must be
     *   queued rather than dropped: the drain runs subtasks before their intervals, so the retry
     *   then succeeds. Dropping would silently lose tracked time in exactly the offline case this
     *   feature exists for.
     */
    private suspend fun pushInterval(
        interval: SubTaskInterval,
        isCreate: Boolean
    ): EmptyResult<DataError> {
        val subTaskId = interval.parentSubTaskId
        val operation = if (isCreate) PendingSyncOperation.OP_CREATE else PendingSyncOperation.OP_UPDATE

        if (pendingSyncDataSource.hasPendingCreate(subTaskId).getOrDefault(false)) {
            return queueForLater(interval.subTaskIntervalId, subTaskId, operation)
        }
        val subTask = parentSubTaskOf(subTaskId)
            ?: return queueForLater(interval.subTaskIntervalId, subTaskId, operation)

        val remoteResult = if (isCreate) {
            remoteSubTaskIntervalDataSource.postInterval(subTask.parentProjectId, subTask.parentProjectTaskId, interval)
        } else {
            remoteSubTaskIntervalDataSource.updateInterval(subTask.parentProjectId, subTask.parentProjectTaskId, interval)
        }

        return when (remoteResult) {
            // Server is canonical on the happy path — but only for the fields it actually has; the
            // data source refills parentTaskIntervalId and startedParentTimer from what was sent.
            is Result.Success ->
                localSubTaskIntervalDataSource.upsertSubTaskInterval(remoteResult.data).asEmptyDataResult()
            is Result.Error -> when {
                isCreate && remoteResult.error == DataError.Remote.CONFLICT ->
                    resolveIntervalConflict(interval, subTask)
                remoteResult.error == DataError.Remote.NOT_FOUND || remoteResult.error.isTransient() ->
                    queueForLater(interval.subTaskIntervalId, subTaskId, operation)
                // Permanent error — the local row stands, nothing left to try.
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    /** A duplicate create means the row is already on the server: push local state as an update. */
    private suspend fun resolveIntervalConflict(
        interval: SubTaskInterval,
        subTask: ProjectSubTask
    ): EmptyResult<DataError> {
        val updated = remoteSubTaskIntervalDataSource
            .updateInterval(subTask.parentProjectId, subTask.parentProjectTaskId, interval)
        return when (updated) {
            is Result.Success ->
                localSubTaskIntervalDataSource.upsertSubTaskInterval(updated.data).asEmptyDataResult()
            is Result.Error -> if (updated.error.isTransient()) {
                queueForLater(
                    interval.subTaskIntervalId,
                    interval.parentSubTaskId,
                    PendingSyncOperation.OP_UPDATE
                )
            } else {
                updated.asEmptyDataResult()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Pending-sync queue draining
    // ---------------------------------------------------------------------------------------------

    override suspend fun syncPendingSubTaskIntervals() {
        val operations = pendingSyncDataSource.getPendingOperations().getOrDefault(emptyList())
        // Drain FIFO so a CREATE is always pushed before a later UPDATE on the same interval.
        operations
            .filter { it.entityType == PendingSyncOperation.ENTITY_SUBTASK_INTERVAL }
            .forEach { op ->
                when (runIntervalOperation(op)) {
                    SyncOutcome.SUCCESS, SyncOutcome.DROP -> pendingSyncDataSource.deleteOperation(op.operationId)
                    SyncOutcome.RETRY -> Unit // leave queued for the next attempt
                }
            }
    }

    private suspend fun runIntervalOperation(op: PendingSyncOperation): SyncOutcome {
        return when (op.operationType) {
            PendingSyncOperation.OP_CREATE, PendingSyncOperation.OP_UPDATE -> {
                val interval = when (val r = localSubTaskIntervalDataSource.getSubTaskIntervalById(op.entityId)) {
                    is Result.Success -> r.data ?: return SyncOutcome.DROP // deleted meanwhile
                    is Result.Error -> return SyncOutcome.RETRY
                }
                // The subtask drain runs before this one, so a still-pending CREATE means that push
                // failed too. Stay queued rather than burning a request that cannot succeed.
                if (pendingSyncDataSource.hasPendingCreate(interval.parentSubTaskId).getOrDefault(false)) {
                    return SyncOutcome.RETRY
                }
                // A missing subtask means it was deleted, and the server cascades that to its
                // intervals — so this op has nothing left to do, and no route to build either.
                val subTask = parentSubTaskOf(interval.parentSubTaskId) ?: return SyncOutcome.DROP
                val result = if (op.operationType == PendingSyncOperation.OP_CREATE) {
                    remoteSubTaskIntervalDataSource.postInterval(subTask.parentProjectId, subTask.parentProjectTaskId, interval)
                } else {
                    remoteSubTaskIntervalDataSource.updateInterval(subTask.parentProjectId, subTask.parentProjectTaskId, interval)
                }
                result.toSyncOutcome(
                    onSuccess = { localSubTaskIntervalDataSource.upsertSubTaskInterval(it) },
                    onConflict = { resolveIntervalConflict(interval, subTask) }
                )
            }
            PendingSyncOperation.OP_DELETE -> {
                val subTaskId = op.parentEntityId ?: return SyncOutcome.DROP
                if (pendingSyncDataSource.hasPendingCreate(subTaskId).getOrDefault(false)) {
                    return SyncOutcome.RETRY
                }
                val subTask = parentSubTaskOf(subTaskId) ?: return SyncOutcome.DROP
                remoteSubTaskIntervalDataSource.deleteInterval(
                    projectId = subTask.parentProjectId,
                    taskId = subTask.parentProjectTaskId,
                    subTaskId = subTaskId,
                    intervalId = op.entityId
                ).toSyncOutcome()
            }
            else -> SyncOutcome.DROP
        }
    }

    /**
     * The parent subtask row, which is where both remaining route segments come from.
     *
     * Unlike a task interval — which carries its own project id and only needs a lookup for a
     * queued DELETE — a subtask interval never carries its task id, so this is needed on every
     * single operation.
     */
    private suspend fun parentSubTaskOf(subTaskId: String): ProjectSubTask? =
        localSubTaskDataSource.getSubTaskById(subTaskId).getOrDefault(null)

    private suspend fun parentTaskIdOf(subTaskId: String): String? =
        parentSubTaskOf(subTaskId)?.parentProjectTaskId

    // ---------------------------------------------------------------------------------------------
    // Queue helpers
    // ---------------------------------------------------------------------------------------------

    /** Queues the op and wakes the scheduler. The local row already stands, so this is the success path. */
    private suspend fun queueForLater(
        intervalId: String,
        subTaskId: String,
        operationType: String
    ): EmptyResult<DataError> {
        // parentEntityId always carries the parent *subtask* id: it is the one link from which both
        // remaining route segments can be recovered once the interval row itself is gone.
        val queued = pendingSyncDataSource.enqueue(
            entityId = intervalId,
            entityType = PendingSyncOperation.ENTITY_SUBTASK_INTERVAL,
            operationType = operationType,
            parentEntityId = subTaskId,
            createdAt = timeProvider.nowInstant
        )
        if (queued is Result.Success) scheduleSync()
        return queued
    }

    private suspend fun scheduleSync() {
        applicationScope.launch { syncScheduler.schedulePeriodicSync() }.join()
    }
}
