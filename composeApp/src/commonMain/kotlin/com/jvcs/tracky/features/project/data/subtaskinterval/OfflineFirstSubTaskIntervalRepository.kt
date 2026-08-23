package com.jvcs.tracky.features.project.data.subtaskinterval

import com.jvcs.tracky.core.domain.sync.PendingSyncDataSource
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.sync.SyncScheduler
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

    /**
     * The parent subtask row, which is where both remaining route segments come from.
     *
     * Unlike a task interval — which carries its own project id and only needs a lookup for a
     * queued DELETE — a subtask interval never carries its task id, so this is needed on every
     * single operation.
     */
    private suspend fun parentSubTaskOf(subTaskId: String): ProjectSubTask? =
        localSubTaskDataSource.getSubTaskById(subTaskId).getOrDefault(null)

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
