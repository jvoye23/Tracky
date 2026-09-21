package com.jvcs.tracky.features.project.data.task

import com.jvcs.tracky.core.domain.device.DeviceIdProvider
import com.jvcs.tracky.core.domain.sync.PendingSyncDataSource
import com.jvcs.tracky.core.domain.timer.ActiveTimerKind
import com.jvcs.tracky.core.domain.timer.ActiveTimerRepository
import com.jvcs.tracky.core.domain.timer.isForeignTimer
import com.jvcs.tracky.core.domain.util.ServerClock
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.sync.SyncOutcome
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.sync.toSyncOutcome
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.startup.StartupReconciliation
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.asEmptyDataResult
import com.jvcs.tracky.core.domain.util.getOrDefault
import com.jvcs.tracky.core.domain.util.isMissingOrForbidden
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
    private val activeTimerRepository: ActiveTimerRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val serverClock: ServerClock,
    private val timeProvider: TimeProvider,
    private val applicationScope: CoroutineScope,
    private val startupReconciliation: StartupReconciliation
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
            is Result.Success -> localTaskDataSource
                .upsertProjectTask(remoteResult.data.withLocalSortIndexFallback(stamped))
                .asEmptyDataResult()
            is Result.Error -> when {
                remoteResult.error == DataError.Remote.CONFLICT -> resolveTaskConflict(stamped)
                // A miss means the parent project is not on the server after all — the backstop
                // for a queue row that went missing. Queue rather than drop, or the task is lost.
                remoteResult.error.isMissingOrForbidden() || remoteResult.error.isTransient() ->
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
                remoteResult.error.isMissingOrForbidden() -> Result.Success(Unit)
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

    override suspend fun updateProjectTaskText(
        taskId: String,
        title: String,
        description: String?
    ): EmptyResult<DataError> {
        // From the stored row, like updateProjectTaskTitle, so the task keeps its sortIndex.
        val task = when (val existing = localTaskDataSource.getTaskById(taskId)) {
            is Result.Success -> existing.data ?: return Result.Success(Unit) // nothing to edit
            is Result.Error -> return existing.asEmptyDataResult()
        }
        return upsertProjectTask(task.copy(title = title, description = description))
    }

    override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> {
        return localTaskDataSource.getTaskWithIntervalsById(taskId)
    }

    override suspend fun startProjectTask(taskId: String): EmptyResult<DataError> {
        // Before anything is opened: startTask reuses whatever interval is already open, so
        // starting ahead of the stranded-timer pass would adopt a row nothing was timing and the
        // next stop would bank every hour since it opened. Free once the pass has run.
        startupReconciliation.awaitReconciled()

        val start = when (val started = localTaskDataSource.startTask(taskId)) {
            is Result.Success -> started.data
            is Result.Error -> return started.asEmptyDataResult()
        }
        // Null means an already-open interval was reused, so there is no new row to create.
        val openedInterval = start.openedInterval ?: return Result.Success(Unit)

        // A task that exists only on this device has no server-side row for the timer resource to
        // hang off, so do not spend a request learning that. The interval queue already knows how
        // to wait for the parent, and the drain pushes tasks before intervals.
        if (pendingSyncDataSource.hasPendingCreate(taskId).getOrDefault(false)) {
            return intervalRepository.createTaskInterval(openedInterval)
        }
        // Otherwise the start goes through the server, which closes whatever was running on the
        // user's other devices and hands back the rows it touched.
        return activeTimerRepository.start(openedInterval)
    }

    override suspend fun stopProjectTask(taskId: String): EmptyResult<DataError> {
        val openInterval = intervalRepository.getOpenIntervalByTaskId(taskId).getOrDefault(null)
        if (openInterval != null &&
            isForeignTimer(openInterval.startedByDeviceId, deviceIdProvider.deviceId())
        ) {
            return stopForeignTimer(openInterval.intervalId)
        }

        val closedInterval = when (val stopped = localTaskDataSource.stopTask(taskId)) {
            is Result.Success -> stopped.data
            is Result.Error -> return stopped.asEmptyDataResult()
        }
        // The interval carries the measured span, the task carries the accumulated total and the
        // cleared timer flag; both have to reach the server. The interval goes first so that a
        // failure pushing the task cannot strand it.
        val intervalResult = if (closedInterval?.endDateTimeUtc != null) {
            // The same compare-and-swap a foreign stop uses. It is this device's own timer, so the
            // local close above is right either way — the server is being told, not asked.
            activeTimerRepository.stop(
                intervalId = closedInterval.intervalId,
                kind = ActiveTimerKind.TASK,
                endedAt = closedInterval.endDateTimeUtc
            )
        } else {
            Result.Success(Unit)
        }
        val task = localTaskDataSource.getTaskById(taskId).getOrDefault(null)
            ?: return intervalResult
        val taskResult = upsertProjectTask(task)
        return if (intervalResult is Result.Error) intervalResult else taskResult
    }

    /**
     * Stops a timer another device started, without banking anything locally.
     *
     * **`localTaskDataSource.stopTask` is deliberately not called.** It is what adds the interval's
     * duration to the task (`ProjectDao.addTaskDuration`), and the server's task row already
     * carries a foreign timer's time — adding it again here would charge the user twice for the
     * same minutes. The echo closes the interval, and the duration arrives with the task row.
     *
     * The end instant is the server-corrected clock rather than this device's. `startedAt` came
     * from the other device, so subtracting a skewed local `now` from it is exactly the error
     * [ServerClock] exists to remove — and here that error would be persisted as tracked time.
     */
    private suspend fun stopForeignTimer(intervalId: String): EmptyResult<DataError> =
        activeTimerRepository.stop(
            intervalId = intervalId,
            kind = ActiveTimerKind.TASK,
            endedAt = serverClock.now()
        )

    // REORDER: persist the manual order of one project's tasks. orderedTaskIds is the new order of
    // the whole list; each task's sortIndex becomes its position in it. A drag is one action for the
    // user, so it is one action here too: one read of the current indices, one transactional local
    // write, one network call. Writing task by task would let a failure halfway through leave two
    // tasks sharing an index, which no retry can repair.
    override suspend fun reorderTasks(
        projectId: String,
        orderedTaskIds: List<String>
    ): EmptyResult<DataError> {
        val current = when (val existing = localTaskDataSource.getTaskSortIndices(projectId)) {
            is Result.Success -> existing.data
            is Result.Error -> return existing.asEmptyDataResult()
        }
        // Only ids that still belong to this project and whose index actually moves.
        val changed = buildMap {
            orderedTaskIds.forEachIndexed { index, taskId ->
                val newIndex = index.toLong()
                if (current.containsKey(taskId) && current[taskId] != newIndex) {
                    put(taskId, newIndex)
                }
            }
        }
        if (changed.isEmpty()) return Result.Success(Unit)

        // One timestamp for both writes — reading the clock twice would stamp the local rows and the
        // server rows with different values for what is a single reorder.
        val updatedAt = timeProvider.nowInstant
        val localResult = localTaskDataSource.updateTaskSortIndices(changed, updatedAt)
        if (localResult !is Result.Success) {
            return localResult.asEmptyDataResult()
        }

        // A project that only exists locally has no /tasks/sort route yet; queue and let the drain
        // push it once the project's own CREATE has landed.
        if (pendingSyncDataSource.hasPendingCreate(projectId).getOrDefault(false)) {
            return enqueueTaskOrderOperation(projectId)
        }

        return when (val remoteResult = remoteTaskDataSource.reorderTasks(projectId, changed, updatedAt)) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> when {
                remoteResult.error.isMissingOrForbidden() || remoteResult.error.isTransient() -> {
                    // Local write already succeeded; only surface an error if queuing the sync fails.
                    enqueueTaskOrderOperation(projectId)
                }
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Pending-sync queue draining
    // ---------------------------------------------------------------------------------------------

    override suspend fun syncPendingTasks() {
        val operations = pendingSyncDataSource.getPendingOperations().getOrDefault(emptyList())
        // Drain FIFO so a CREATE is always pushed before a later UPDATE on the same task.
        operations
            .filter {
                it.entityType == PendingSyncOperation.ENTITY_TASK ||
                    it.entityType == PendingSyncOperation.ENTITY_TASK_ORDER
            }
            .forEach { op ->
                when (runTaskOperation(op)) {
                    SyncOutcome.SUCCESS, SyncOutcome.DROP -> pendingSyncDataSource.deleteOperation(op.operationId)
                    SyncOutcome.RETRY -> Unit // leave queued for the next attempt
                }
            }
    }

    private suspend fun runTaskOperation(op: PendingSyncOperation): SyncOutcome {
        if (op.entityType == PendingSyncOperation.ENTITY_TASK_ORDER) {
            return runTaskOrderOperation(op)
        }
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
                    onSuccess = { localTaskDataSource.upsertProjectTask(it.withLocalSortIndexFallback(task)) },
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
                    val merged = pushed.data.withLocalSortIndexFallback(local)
                    applicationScope.async { localTaskDataSource.upsertProjectTask(merged) }.await()
                    Result.Success(Unit)
                }
                is Result.Error -> pushed.asEmptyDataResult()
            }
        } else {
            // Server wins on freshness, but keep the local sortIndex when the server has none.
            val merged = server.withLocalSortIndexFallback(local)
            applicationScope.async { localTaskDataSource.upsertProjectTask(merged) }.await()
            Result.Success(Unit)
        }
    }

    /**
     * Keeps the locally known order when the server has no sortIndex of its own. Without this, any
     * ordinary edit (rename, finish, a timer stop) would silently wipe the order the user dragged.
     * The project repository carries the same guard one level up.
     */
    private fun ProjectTask.withLocalSortIndexFallback(local: ProjectTask): ProjectTask =
        if (sortIndex != null) this else copy(sortIndex = local.sortIndex)

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

    /**
     * The queued row is just a marker: the order itself is rebuilt from current local state, so
     * tasks deleted meanwhile drop out and repeated offline reorders collapse into one push.
     */
    private suspend fun runTaskOrderOperation(op: PendingSyncOperation): SyncOutcome {
        val projectId = op.parentEntityId ?: return SyncOutcome.DROP
        if (pendingSyncDataSource.hasPendingCreate(projectId).getOrDefault(false)) {
            return SyncOutcome.RETRY
        }
        val indices = when (val r = localTaskDataSource.getTaskSortIndices(projectId)) {
            is Result.Success -> r.data.mapNotNull { (id, index) -> index?.let { id to it } }.toMap()
            is Result.Error -> return SyncOutcome.RETRY
        }
        if (indices.isEmpty()) return SyncOutcome.DROP
        return remoteTaskDataSource
            .reorderTasks(projectId, indices, timeProvider.nowInstant)
            .toSyncOutcome()
    }

    /** One queue row per project, so repeat reorders of the same project collapse. */
    private suspend fun enqueueTaskOrderOperation(projectId: String): EmptyResult<DataError> {
        val queued = pendingSyncDataSource.enqueue(
            entityId = PendingSyncOperation.taskOrderEntityId(projectId),
            entityType = PendingSyncOperation.ENTITY_TASK_ORDER,
            operationType = PendingSyncOperation.OP_UPDATE,
            // Unlike the other ops there is no row to re-read the project from at drain time, so
            // the parent is stored even though this is not a DELETE.
            parentEntityId = projectId,
            createdAt = timeProvider.nowInstant
        )
        if (queued is Result.Success) scheduleSync()
        return queued
    }

    private suspend fun scheduleSync() {
        applicationScope.launch { syncScheduler.schedulePeriodicSync() }.join()
    }
}
