package com.jvcs.tracky.features.project.data.subtask

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
import com.jvcs.tracky.core.domain.util.isMissingOrForbidden
import com.jvcs.tracky.core.domain.util.isTransient
import com.jvcs.tracky.features.project.domain.interval.IntervalRepository
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.subtask.LocalSubTaskDataSource
import com.jvcs.tracky.features.project.domain.subtask.RemoteSubTaskDataSource
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.subtask.SubTaskTimerChange
import com.jvcs.tracky.features.project.domain.subtaskinterval.SubTaskIntervalRepository
import com.jvcs.tracky.features.project.domain.task.LocalTaskDataSource
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Subtasks sync through their own routes nested under the task, one level below where the task
 * repository works: `/api/projects/{projectId}/tasks/{taskId}/subtasks`. The offline-first rules
 * are the task repository's, applied a level down — optimistic local write, queue on a failure the
 * server might yet accept, last-write-wins on a conflict.
 *
 * What still goes elsewhere is the *task* interval a subtask timer opens or closes. Those rows are
 * pushed through the interval and task repositories, which already own the queueing and conflict
 * rules for them, rather than being duplicated here.
 */
class OfflineFirstSubTaskRepository(
    private val localSubTaskDataSource: LocalSubTaskDataSource,
    private val remoteSubTaskDataSource: RemoteSubTaskDataSource,
    private val localTaskDataSource: LocalTaskDataSource,
    private val intervalRepository: IntervalRepository,
    private val subTaskIntervalRepository: SubTaskIntervalRepository,
    private val projectTaskRepository: ProjectTaskRepository,
    private val pendingSyncDataSource: PendingSyncDataSource,
    private val syncScheduler: SyncScheduler,
    private val applicationScope: CoroutineScope,
    private val timeProvider: TimeProvider
) : SubTaskRepository {

    override fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>> =
        localSubTaskDataSource.getSubTasksForTask(taskId)

    // CREATE/UPDATE subtask: local first (optimistic), then remote; on transient failure -> queue.
    override suspend fun upsertSubTask(subTask: ProjectSubTask): EmptyResult<DataError> {
        val isCreate = when (val existing = localSubTaskDataSource.getSubTaskById(subTask.projectSubTaskId)) {
            is Result.Success -> existing.data == null
            is Result.Error -> return existing.asEmptyDataResult()
        }
        val stamped = subTask.copy(ownUpdatedAt = timeProvider.nowInstant)

        val localResult = localSubTaskDataSource.upsertSubTask(stamped)
        if (localResult !is Result.Success) return localResult.asEmptyDataResult()

        return pushSubTask(stamped, isCreate)
    }

    // DELETE subtask: local first, then remote; handle offline-create-then-delete (ghost) case.
    override suspend fun deleteSubTask(subTaskId: String): EmptyResult<DataError> {
        val subTask = localSubTaskDataSource.getSubTaskById(subTaskId)
            .getOrDefault(null) ?: return Result.Success(Unit) // already gone locally
        val hadPendingCreate = pendingSyncDataSource.hasPendingCreate(subTaskId).getOrDefault(false)

        val localResult = localSubTaskDataSource.deleteSubTask(subTaskId)
        if (localResult !is Result.Success) return localResult.asEmptyDataResult()

        if (hadPendingCreate) {
            // Created offline and deleted before it ever reached the server -> just drop the queue.
            pendingSyncDataSource.deleteOperationsByEntityId(subTaskId)
            return Result.Success(Unit)
        }

        val taskId = subTask.parentProjectTaskId
        // The parent task was created offline too, so the server has neither it nor this subtask —
        // and deleting the task later takes its subtasks with it by cascade. Nothing to push.
        if (pendingSyncDataSource.hasPendingCreate(taskId).getOrDefault(false)) {
            return Result.Success(Unit)
        }

        val remoteResult = applicationScope.async {
            remoteSubTaskDataSource.deleteSubTask(subTask.parentProjectId, taskId, subTaskId)
        }.await()
        return when (remoteResult) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> when {
                // Local delete already succeeded; only surface an error if queuing the sync fails.
                remoteResult.error.isTransient() ->
                    queueForLater(subTaskId, taskId, PendingSyncOperation.OP_DELETE)
                // Server already has no such subtask -> the delete is effectively done.
                remoteResult.error.isMissingOrForbidden() -> Result.Success(Unit)
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    /**
     * Starting a subtask changes up to four rows that sync, and every one of them is pushed.
     *
     * The order is measured time before derived state, outer before inner: the two interval rows
     * carry spans that exist nowhere else, while the task and subtask rows carry only a duration
     * and a timer flag, both recomputable from those intervals. So a failure on either interval is
     * the one worth reporting, and the first error in push order wins.
     *
     * Nothing is skipped when an earlier push fails. Each of the four has its own offline queue, so
     * stopping early would silently drop writes that could have been queued.
     */
    override suspend fun startSubTask(subTaskId: String): EmptyResult<DataError> {
        val change = when (val started = localSubTaskDataSource.startSubTask(subTaskId)) {
            is Result.Success -> started.data
            is Result.Error -> return started.asEmptyDataResult()
        }
        return pushTimerChange(subTaskId, change, isCreate = true)
    }

    /** Stops the subtask's timer. Pushes the same four rows [startSubTask] does — see its KDoc. */
    override suspend fun stopSubTask(subTaskId: String): EmptyResult<DataError> {
        val change = when (val stopped = localSubTaskDataSource.stopSubTask(subTaskId)) {
            is Result.Success -> stopped.data ?: return Result.Success(Unit) // timer was not running
            is Result.Error -> return stopped.asEmptyDataResult()
        }
        return pushTimerChange(subTaskId, change, isCreate = false)
    }

    private suspend fun pushTimerChange(
        subTaskId: String,
        change: SubTaskTimerChange,
        isCreate: Boolean
    ): EmptyResult<DataError> {
        // Null when the task's timer was already running (or is left running): that interval is
        // already on its way to the server, and the task's own row has not changed either.
        val taskInterval = change.taskInterval
        val taskIntervalResult = taskInterval?.let {
            if (isCreate) intervalRepository.createTaskInterval(it)
            else intervalRepository.updateTaskInterval(it)
        }

        val subTaskIntervalResult = if (isCreate) {
            subTaskIntervalRepository.createSubTaskInterval(change.subTaskInterval)
        } else {
            subTaskIntervalRepository.updateSubTaskInterval(change.subTaskInterval)
        }

        // Re-read rather than reusing the pre-write copy: the data source is what banked the
        // duration and cleared the flag, so this is the only place the new values exist.
        val subTask = localSubTaskDataSource.getSubTaskById(subTaskId).getOrDefault(null)

        val taskResult = if (taskInterval != null && subTask != null) {
            pushParentTask(subTask.parentProjectTaskId)
        } else {
            null
        }
        val subTaskResult = subTask?.let { upsertSubTask(it) }

        return firstError(taskIntervalResult, subTaskIntervalResult, taskResult, subTaskResult)
            ?: Result.Success(Unit)
    }

    private fun firstError(vararg results: EmptyResult<DataError>?): EmptyResult<DataError>? =
        results.filterIsInstance<Result.Error<DataError>>().firstOrNull()

    /**
     * Pushes the parent task row, whose duration and timer flag the write just changed.
     *
     * Goes through [ProjectTaskRepository] rather than talking to the network directly so the task's
     * queueing and last-write-wins rules stay in one place.
     */
    private suspend fun pushParentTask(taskId: String): EmptyResult<DataError>? {
        val task = localTaskDataSource.getTaskById(taskId).getOrDefault(null) ?: return null
        return projectTaskRepository.upsertProjectTask(task)
    }

    /**
     * Pushes one subtask, queueing it whenever the push cannot succeed yet.
     *
     * Mirrors the task repository's rules one level down. Two cases never reach the network, or come
     * back from it, as an ordinary failure:
     * - The parent task is still queued for creation. There is no route to POST to yet, so the
     *   subtask is queued straight away rather than spending a request to learn that.
     * - A miss means the parent task does not exist server-side after all — the backstop for a
     *   queue row that went missing. That must be queued rather than dropped: the drain runs tasks
     *   before subtasks, so the retry then succeeds.
     */
    private suspend fun pushSubTask(subTask: ProjectSubTask, isCreate: Boolean): EmptyResult<DataError> {
        val taskId = subTask.parentProjectTaskId
        val projectId = subTask.parentProjectId
        val operation = if (isCreate) PendingSyncOperation.OP_CREATE else PendingSyncOperation.OP_UPDATE

        if (pendingSyncDataSource.hasPendingCreate(taskId).getOrDefault(false)) {
            return queueForLater(subTask.projectSubTaskId, taskId, operation)
        }

        val remoteResult = if (isCreate) {
            remoteSubTaskDataSource.postSubTask(projectId, taskId, subTask)
        } else {
            remoteSubTaskDataSource.updateSubTask(projectId, taskId, subTask)
        }

        return when (remoteResult) {
            // Server is canonical on the happy path, exactly like projects and tasks.
            is Result.Success -> localSubTaskDataSource.upsertSubTask(remoteResult.data).asEmptyDataResult()
            is Result.Error -> when {
                remoteResult.error == DataError.Remote.CONFLICT -> resolveSubTaskConflict(subTask)
                remoteResult.error.isMissingOrForbidden() || remoteResult.error.isTransient() ->
                    queueForLater(subTask.projectSubTaskId, taskId, operation)
                // Permanent error — the local row stands, nothing left to try.
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Conflict resolution (last-write-wins on the newer updatedAt)
    // ---------------------------------------------------------------------------------------------

    /**
     * Unlike an interval, a subtask is edited by hand and carries a real stamp, so a duplicate is
     * resolved by comparing the two rows rather than blindly retrying as an update.
     */
    private suspend fun resolveSubTaskConflict(local: ProjectSubTask): EmptyResult<DataError> {
        val projectId = local.parentProjectId
        val taskId = local.parentProjectTaskId
        val server = when (val r = remoteSubTaskDataSource.getSubTasksByTaskId(projectId, taskId)) {
            is Result.Success -> r.data.find { it.projectSubTaskId == local.projectSubTaskId }
            is Result.Error -> return r.asEmptyDataResult()
        } ?: return remoteSubTaskDataSource // server has none -> push local
            .postSubTask(projectId, taskId, local)
            .asEmptyDataResult()

        // Compares this subtask row against the same row on the server, so it must read the row's
        // own stamp — never the lastUpdatedAt roll-up over its intervals.
        return if (local.ownUpdatedAt != null && (server.ownUpdatedAt == null || local.ownUpdatedAt > server.ownUpdatedAt)) {
            when (val pushed = remoteSubTaskDataSource.updateSubTask(projectId, taskId, local)) {
                is Result.Success -> {
                    applicationScope.async { localSubTaskDataSource.upsertSubTask(pushed.data) }.await()
                    Result.Success(Unit)
                }
                is Result.Error -> pushed.asEmptyDataResult()
            }
        } else {
            applicationScope.async { localSubTaskDataSource.upsertSubTask(server) }.await()
            Result.Success(Unit)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Pending-sync queue draining
    // ---------------------------------------------------------------------------------------------

    override suspend fun syncPendingSubTasks() {
        val operations = pendingSyncDataSource.getPendingOperations().getOrDefault(emptyList())
        // Drain FIFO so a CREATE is always pushed before a later UPDATE on the same subtask.
        operations
            .filter { it.entityType == PendingSyncOperation.ENTITY_SUBTASK }
            .forEach { op ->
                when (runSubTaskOperation(op)) {
                    SyncOutcome.SUCCESS, SyncOutcome.DROP -> pendingSyncDataSource.deleteOperation(op.operationId)
                    SyncOutcome.RETRY -> Unit // leave queued for the next attempt
                }
            }
    }

    private suspend fun runSubTaskOperation(op: PendingSyncOperation): SyncOutcome {
        return when (op.operationType) {
            PendingSyncOperation.OP_CREATE, PendingSyncOperation.OP_UPDATE -> {
                val subTask = when (val r = localSubTaskDataSource.getSubTaskById(op.entityId)) {
                    is Result.Success -> r.data ?: return SyncOutcome.DROP // deleted meanwhile
                    is Result.Error -> return SyncOutcome.RETRY
                }
                // The task drain runs before this one, so a still-pending CREATE means that push
                // failed too. Stay queued rather than burning a request that cannot succeed.
                if (pendingSyncDataSource.hasPendingCreate(subTask.parentProjectTaskId).getOrDefault(false)) {
                    return SyncOutcome.RETRY
                }
                val projectId = subTask.parentProjectId
                val taskId = subTask.parentProjectTaskId
                val result = if (op.operationType == PendingSyncOperation.OP_CREATE) {
                    remoteSubTaskDataSource.postSubTask(projectId, taskId, subTask)
                } else {
                    remoteSubTaskDataSource.updateSubTask(projectId, taskId, subTask)
                }
                result.toSyncOutcome(
                    onSuccess = { localSubTaskDataSource.upsertSubTask(it) },
                    onConflict = { resolveSubTaskConflict(subTask) }
                )
            }
            PendingSyncOperation.OP_DELETE -> {
                val taskId = op.parentEntityId ?: return SyncOutcome.DROP
                if (pendingSyncDataSource.hasPendingCreate(taskId).getOrDefault(false)) {
                    return SyncOutcome.RETRY
                }
                // A missing task row means the task was deleted, and the server cascades that to
                // its subtasks — so this delete has nothing left to do. Retrying would never end.
                val projectId = parentProjectIdOf(taskId) ?: return SyncOutcome.DROP
                remoteSubTaskDataSource.deleteSubTask(projectId, taskId, op.entityId).toSyncOutcome()
            }
            else -> SyncOutcome.DROP
        }
    }

    /**
     * Resolves a subtask route's project id from the task alone.
     *
     * Subtasks carry their own `parentProjectId`, so this is only needed for a queued DELETE: by the
     * time that op drains, the local subtask row is gone and the task id stored on the queue entry
     * is all that is left to go on.
     */
    private suspend fun parentProjectIdOf(taskId: String): String? =
        localTaskDataSource.getTaskById(taskId).getOrDefault(null)?.parentProjectId

    // ---------------------------------------------------------------------------------------------
    // Queue helpers
    // ---------------------------------------------------------------------------------------------

    /** Queues the op and wakes the scheduler. The local row already stands, so this is the success path. */
    private suspend fun queueForLater(
        subTaskId: String,
        taskId: String,
        operationType: String
    ): EmptyResult<DataError> {
        // parentEntityId is only needed for DELETE (the subtask row is gone by drain time); for the
        // other ops both ids are re-read from the subtask itself.
        val parent = if (operationType == PendingSyncOperation.OP_DELETE) taskId else null
        val queued = pendingSyncDataSource.enqueue(
            entityId = subTaskId,
            entityType = PendingSyncOperation.ENTITY_SUBTASK,
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
