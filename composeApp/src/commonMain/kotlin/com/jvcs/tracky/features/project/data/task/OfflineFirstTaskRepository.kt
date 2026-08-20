package com.jvcs.tracky.features.project.data.task

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
import com.jvcs.tracky.features.project.domain.interval.IntervalRepository
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.task.LocalTaskDataSource
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.domain.task.RemoteTaskDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Tasks sit in the middle of the sync chain: their route is nested under a project, and intervals
 * hang off them in turn. A task whose project has not reached the server yet has nowhere to go, so
 * every write asks the queue about the parent before spending a request.
 */
class OfflineFirstTaskRepository(
    private val localTaskDataSource: LocalTaskDataSource,
    private val remoteTaskDataSource: RemoteTaskDataSource,
    private val pendingSyncDataSource: PendingSyncDataSource,
    private val syncScheduler: SyncScheduler,
    private val intervalRepository: IntervalRepository,
    private val timeProvider: TimeProvider,
    private val applicationScope: CoroutineScope
) : ProjectTaskRepository {

    // CREATE/UPDATE task: local first (optimistic), then remote; on transient failure → queue.
    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> {
        val isCreate = when (val existing = localTaskDataSource.getTaskById(projectTask.projectTaskId)) {
            is Result.Success -> existing.data == null
            is Result.Error -> return existing.asEmptyDataResult()
        }
        val stamped = projectTask.copy(ownUpdatedAt = timeProvider.nowInstant)

        val localResult = localTaskDataSource.upsertProjectTask(stamped)
        if (localResult !is Result.Success) {
            return localResult.asEmptyDataResult()
        }

        val operation = if (isCreate) PendingSyncOperation.OP_CREATE else PendingSyncOperation.OP_UPDATE
        val projectId = stamped.parentProjectId

        // The task routes are nested under the project, so a project that exists only on this
        // device has no route to POST to. Queue instead of calling — the drain pushes the project's
        // own CREATE first and the task follows in the same pass.
        if (pendingSyncDataSource.hasPendingCreate(projectId).getOrDefault(false)) {
            return queueForLater(stamped.projectTaskId, projectId, operation)
        }

        val remoteResult = if (isCreate) {
            remoteTaskDataSource.postTaskByProjectId(projectId, stamped)
        } else {
            remoteTaskDataSource.updateTaskByProjectId(projectId, stamped)
        }
        return when (remoteResult) {
            // Server is canonical on the happy path, exactly like projects and intervals.
            is Result.Success -> localTaskDataSource.upsertProjectTask(remoteResult.data).asEmptyDataResult()
            is Result.Error -> when {
                remoteResult.error == DataError.Remote.CONFLICT -> resolveTaskConflict(stamped)
                // NOT_FOUND means the parent project is missing server-side after all — the backstop
                // for a queue row that went missing. Queue rather than drop, or the task is lost.
                remoteResult.error == DataError.Remote.NOT_FOUND || remoteResult.error.isTransient() ->
                    queueForLater(stamped.projectTaskId, projectId, operation)
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    // DELETE task: local first, then remote; handle offline-create-then-delete (ghost) case.
    override suspend fun deleteProjectTask(projectId: String, taskId: String): EmptyResult<DataError> {
        val hadPendingCreate = pendingSyncDataSource.hasPendingCreate(taskId).getOrDefault(false)

        val localResult = localTaskDataSource.deleteProjectTask(taskId)
        if (localResult !is Result.Success) return localResult.asEmptyDataResult()

        if (hadPendingCreate) {
            // Created offline and deleted before it ever reached the server → just drop the queue.
            pendingSyncDataSource.deleteOperationsByEntityId(taskId)
            return Result.Success(Unit)
        }

        // The parent project was created offline too, so the server has neither it nor this task —
        // and deleting the project later takes its tasks with it by cascade. Nothing to push.
        if (pendingSyncDataSource.hasPendingCreate(projectId).getOrDefault(false)) {
            return Result.Success(Unit)
        }

        val remoteResult = applicationScope.async {
            remoteTaskDataSource.deleteTask(projectId = projectId, taskId = taskId)
        }.await()
        return when (remoteResult) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> when {
                remoteResult.error.isTransient() -> {
                    // Local delete already succeeded; only surface an error if queuing the sync fails.
                    queueForLater(taskId, projectId, PendingSyncOperation.OP_DELETE)
                }
                // Server already has no such task → the delete is effectively done.
                remoteResult.error == DataError.Remote.NOT_FOUND -> Result.Success(Unit)
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    // Purely local: the duration is recomputed from the intervals the server already has, so there
    // is nothing to push for it.
    override suspend fun updateProjectTaskDuration(
        taskId: String,
        newDurationMillis: Long
    ): EmptyResult<DataError> {
        return localTaskDataSource.updateTaskDuration(taskId, newDurationMillis).asEmptyDataResult()
    }

    // Title edits must reach the server too. Route through the offline-first upsert so the change
    // is pushed remotely (and queued for sync when offline) instead of staying local-only.
    override suspend fun updateProjectTaskTitle(taskId: String, title: String): EmptyResult<DataError> {
        val task = when (val existing = localTaskDataSource.getTaskById(taskId)) {
            is Result.Success -> existing.data ?: return Result.Success(Unit) // nothing to rename
            is Result.Error -> return existing.asEmptyDataResult()
        }
        return upsertProjectTask(task.copy(title = title))
    }

    override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> {
        return localTaskDataSource.getTaskWithIntervalsById(taskId)
    }

    override suspend fun startProjectTask(taskId: String): EmptyResult<DataError> {
        val openedInterval = when (val started = localTaskDataSource.startTask(taskId)) {
            is Result.Success -> started.data
            is Result.Error -> return started.asEmptyDataResult()
        }
        return intervalRepository.createTaskInterval(openedInterval)
    }

    override suspend fun stopProjectTask(taskId: String): EmptyResult<DataError> {
        val closedInterval = when (val stopped = localTaskDataSource.stopTask(taskId)) {
            is Result.Success -> stopped.data
            is Result.Error -> return stopped.asEmptyDataResult()
        }
        // The interval carries the measured span, the task carries the accumulated total and the
        // cleared timer flag; both have to reach the server. The interval goes first so that a
        // failure pushing the task cannot strand it.
        val intervalResult = if (closedInterval != null) {
            intervalRepository.updateTaskInterval(closedInterval)
        } else {
            Result.Success(Unit)
        }
        val task = localTaskDataSource.getTaskById(taskId).getOrDefault(null)
            ?: return intervalResult
        val taskResult = upsertProjectTask(task)
        return if (intervalResult is Result.Error) intervalResult else taskResult
    }

    // ---------------------------------------------------------------------------------------------
    // Pending-sync queue draining
    // ---------------------------------------------------------------------------------------------

    override suspend fun syncPendingTasks() {
        val operations = pendingSyncDataSource.getPendingOperations().getOrDefault(emptyList())
        // Drain FIFO so a CREATE is always pushed before a later UPDATE on the same task.
        operations
            .filter { it.entityType == PendingSyncOperation.ENTITY_TASK }
            .forEach { op ->
                when (runTaskOperation(op)) {
                    SyncOutcome.SUCCESS, SyncOutcome.DROP -> pendingSyncDataSource.deleteOperation(op.operationId)
                    SyncOutcome.RETRY -> Unit // leave queued for the next attempt
                }
            }
    }

    private suspend fun runTaskOperation(op: PendingSyncOperation): SyncOutcome {
        return when (op.operationType) {
            PendingSyncOperation.OP_CREATE, PendingSyncOperation.OP_UPDATE -> {
                val task = when (val r = localTaskDataSource.getTaskById(op.entityId)) {
                    is Result.Success -> r.data ?: return SyncOutcome.DROP // deleted meanwhile
                    is Result.Error -> return SyncOutcome.RETRY
                }
                // The project drain runs before this one, so a still-pending CREATE means that push
                // failed too. Stay queued rather than burning a request that cannot succeed.
                if (pendingSyncDataSource.hasPendingCreate(task.parentProjectId).getOrDefault(false)) {
                    return SyncOutcome.RETRY
                }
                val result = if (op.operationType == PendingSyncOperation.OP_CREATE) {
                    remoteTaskDataSource.postTaskByProjectId(task.parentProjectId, task)
                } else {
                    remoteTaskDataSource.updateTaskByProjectId(task.parentProjectId, task)
                }
                result.toSyncOutcome(
                    onSuccess = { localTaskDataSource.upsertProjectTask(it) },
                    onConflict = { resolveTaskConflict(task) }
                )
            }
            PendingSyncOperation.OP_DELETE -> {
                val parentProjectId = op.parentEntityId ?: return SyncOutcome.DROP
                if (pendingSyncDataSource.hasPendingCreate(parentProjectId).getOrDefault(false)) {
                    return SyncOutcome.RETRY
                }
                remoteTaskDataSource.deleteTask(parentProjectId, op.entityId).toSyncOutcome()
            }
            else -> SyncOutcome.DROP
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Conflict resolution (last-write-wins on the newer updatedAt)
    // ---------------------------------------------------------------------------------------------

    private suspend fun resolveTaskConflict(local: ProjectTask): EmptyResult<DataError> {
        val server = when (val r = remoteTaskDataSource.getTasksByProjectId(local.parentProjectId)) {
            is Result.Success -> r.data.find { it.projectTaskId == local.projectTaskId }
            is Result.Error -> return r.asEmptyDataResult()
        } ?: return remoteTaskDataSource // server has none → push local
            .postTaskByProjectId(local.parentProjectId, local)
            .asEmptyDataResult()

        // Last-write-wins compares this task row against the same row on the server, so it must
        // read the row's own stamp — never the lastUpdatedAt roll-up over its intervals.
        return if (local.ownUpdatedAt != null && (server.ownUpdatedAt == null || local.ownUpdatedAt > server.ownUpdatedAt)) {
            when (val pushed = remoteTaskDataSource.updateTaskByProjectId(local.parentProjectId, local)) {
                is Result.Success -> {
                    applicationScope.async { localTaskDataSource.upsertProjectTask(pushed.data) }.await()
                    Result.Success(Unit)
                }
                is Result.Error -> pushed.asEmptyDataResult()
            }
        } else {
            applicationScope.async { localTaskDataSource.upsertProjectTask(server) }.await()
            Result.Success(Unit)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Queue helpers
    // ---------------------------------------------------------------------------------------------

    /** Queues the op and wakes the scheduler. The local row already stands, so this is the success path. */
    private suspend fun queueForLater(
        taskId: String,
        parentProjectId: String,
        operationType: String
    ): EmptyResult<DataError> {
        // parentEntityId is only needed for DELETE (the task row is gone by drain time); for the
        // other ops the project id is re-read from the task itself.
        val parent = if (operationType == PendingSyncOperation.OP_DELETE) parentProjectId else null
        val queued = pendingSyncDataSource.enqueue(
            entityId = taskId,
            entityType = PendingSyncOperation.ENTITY_TASK,
            operationType = operationType,
            parentEntityId = parent,
            createdAt = timeProvider.nowInstant
        )
        if (queued is Result.Success) scheduleSync()
        return queued
    }

    private suspend fun scheduleSync() {
        applicationScope.launch { syncScheduler.schedulePeriodicSync() }.join()
    }
}
