package com.jvcs.tracky.features.project.data.project

import com.jvcs.tracky.core.domain.sync.PendingSyncDataSource
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.sync.SyncOutcome
import com.jvcs.tracky.core.domain.sync.SyncRepository
import com.jvcs.tracky.core.domain.sync.toSyncOutcome
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.asEmptyDataResult
import com.jvcs.tracky.core.domain.util.getOrDefault
import com.jvcs.tracky.core.domain.util.isTransient
import com.jvcs.tracky.core.domain.util.onSuccess
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.project.LocalProjectDataSource
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.project.RemoteProjectDataSource
import com.jvcs.tracky.features.project.domain.project.sortedByCustomOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Instant

class OfflineFirstProjectRepository(
    private val localProjectDataSource: LocalProjectDataSource,
    private val remoteProjectDataSource: RemoteProjectDataSource,
    private val pendingSyncDataSource: PendingSyncDataSource,
    private val syncScheduler: SyncScheduler,
    private val applicationScope: CoroutineScope,
    private val timeProvider: TimeProvider
): ProjectRepository, SyncRepository {

    // PULL: network → Room. The Room Flow the UI observes emits automatically.
    override suspend fun fetchProjects(): Result<List<Project>, DataError.Remote> {
        return remoteProjectDataSource
            .getProjects()
            .onSuccess { projects ->
                localProjectDataSource.upsertProjects(projects)
            }
    }

    override fun getProjects(): Flow<List<Project>> {
        return localProjectDataSource.getProjects()
    }

    override fun getActiveProjects(): Flow<List<Project>> {
        return localProjectDataSource.getActiveProjects()
    }

    override fun getArchivedProjects(): Flow<List<Project>> {
        return localProjectDataSource.getArchivedProjects()
    }

    override fun getTrashedProjects(): Flow<List<Project>> {
        return localProjectDataSource.getTrashedProjects()
    }

    override suspend fun getProjectById(projectId: String): Project? {
        return localProjectDataSource.getProjectById(projectId).getOrDefault(null)
    }

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? {
        return localProjectDataSource.getProjectWithTasksByProjectId(projectId).getOrDefault(null)
    }

    // CREATE/UPDATE project: local first (optimistic), then remote; on transient failure → queue.
    override suspend fun upsertProject(project: Project): EmptyResult<DataError> {
        val isCreate = when (val existing = localProjectDataSource.getProjectById(project.projectId)) {
            is Result.Success -> existing.data == null
            is Result.Error -> return existing.asEmptyDataResult()
        }
        val stamped = project.copy(ownUpdatedAt = timeProvider.nowInstant)

        val localResult = localProjectDataSource.upsertProject(stamped)
        if (localResult !is Result.Success) {
            return localResult.asEmptyDataResult()
        }

        val remoteResult = if (isCreate) {
            remoteProjectDataSource.postProject(stamped)
        } else {
            remoteProjectDataSource.updateProject(stamped)
        }
        return when (remoteResult) {
            is Result.Success -> {
                // Server is canonical (server-wins on the happy path), except that an echo without a
                // sortIndex must not wipe the manual order — same guard as resolveProjectConflict.
                localProjectDataSource.upsertProject(remoteResult.data.withLocalSortIndexFallback(stamped))
                Result.Success(Unit)
            }
            is Result.Error -> when {
                remoteResult.error == DataError.Remote.CONFLICT -> resolveProjectConflict(stamped)
                remoteResult.error.isTransient() -> {
                    // Local write already succeeded; only surface an error if queuing the sync fails.
                    val queued = enqueueProjectOperation(project.projectId, if (isCreate) PendingSyncOperation.OP_CREATE else PendingSyncOperation.OP_UPDATE)
                    if (queued is Result.Success) scheduleSync()
                    queued
                }
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    // ARCHIVE/UNARCHIVE: flip the flag and route through the offline-first upsert so the change is
    // pushed to the server immediately when online (and only queued for sync when offline), exactly
    // like every other write.
    override suspend fun setProjectArchived(projectId: String, isArchived: Boolean): EmptyResult<DataError> {
        val project = when (val existing = localProjectDataSource.getProjectById(projectId)) {
            is Result.Success -> existing.data ?: return Result.Success(Unit) // nothing to archive
            is Result.Error -> return existing.asEmptyDataResult()
        }
        return upsertProject(project.copy(isArchived = isArchived))
    }

    // SOFT-DELETE/RESTORE: stamp (or clear) trashedAt and route through the offline-first upsert so
    // the change is pushed to the server immediately when online (and queued when offline), exactly
    // like archive. A non-null trashedAt trashes the project; null restores it.
    override suspend fun setProjectTrashed(projectId: String, trashedAt: Instant?): EmptyResult<DataError> {
        val project = when (val existing = localProjectDataSource.getProjectById(projectId)) {
            is Result.Success -> existing.data ?: return Result.Success(Unit) // nothing to trash
            is Result.Error -> return existing.asEmptyDataResult()
        }
        return upsertProject(project.copy(trashedAt = trashedAt))
    }

    // PURGE: permanently delete every project whose trashedAt is older than the cutoff, locally and
    // on the server. Reuses deleteProject so each removal gets the server DELETE + offline fallback.
    override suspend fun purgeExpiredTrashedProjects(cutoff: Instant): EmptyResult<DataError> =
        coroutineScope {
            val expiredIds = when (val result = localProjectDataSource.getExpiredTrashedProjectIds(cutoff)) {
                is Result.Success -> result.data
                is Result.Error -> return@coroutineScope result.asEmptyDataResult()
            }
            expiredIds.map { async { deleteProject(it) } }.awaitAll()
            Result.Success(Unit)
        }

    // PIN/UNPIN: flip the flag and route through the offline-first upsert, exactly like archive —
    // then move the affected projects to the top of the section they just entered and re-index the
    // rest of it. Without that second step a project keeps the index it held in its old section and
    // collides with whatever already sits there, so it lands wherever its creation date puts it.
    override suspend fun setProjectsPinned(projectIds: List<String>, isPinned: Boolean): EmptyResult<DataError> {
        if (projectIds.isEmpty()) return Result.Success(Unit)

        val moved = mutableListOf<String>()
        var firstError: EmptyResult<DataError>? = null
        for (projectId in projectIds) {
            val project = when (val existing = localProjectDataSource.getProjectById(projectId)) {
                is Result.Success -> existing.data ?: continue // nothing to pin
                is Result.Error -> {
                    firstError = firstError ?: existing.asEmptyDataResult()
                    continue
                }
            }
            when (val flipped = upsertProject(project.copy(isPinned = isPinned))) {
                is Result.Success -> moved += projectId
                is Result.Error -> firstError = firstError ?: flipped
            }
        }
        if (moved.isEmpty()) return firstError ?: Result.Success(Unit)

        // The moved projects go first, keeping the relative order they already had; everyone else in
        // the target section keeps its order behind them. reorderProjects then numbers the whole
        // section from 0 in one transaction and one request.
        val section = when (val allPinnedProjects = localProjectDataSource.getPinnedProjects()) {
            is Result.Success -> allPinnedProjects.data
            is Result.Error -> return firstError ?: allPinnedProjects.asEmptyDataResult()
        }
        val movedIds = moved.toSet()
        val (front, rest) = section.sortedByCustomOrder()
            .map { it.projectId }
            .partition { it in movedIds }

        val reordered = reorderProjects(front + rest)
        return firstError ?: reordered
    }

    // REORDER: persist the manual order shown under the Custom sort filter. orderedProjectIds is the
    // new order of a single section (Pinned or Other); each project's sortIndex is set to its position
    // in that list. A drag is one action for the user, so it is one action here too: one read of the
    // current indices, one transactional local write, one network call. Writing card by card would
    // let a failure halfway through leave two projects sharing an index, which no retry can repair.
    override suspend fun reorderProjects(orderedProjectIds: List<String>): EmptyResult<DataError> {
        val current = when (val existing = localProjectDataSource.getSortIndices()) {
            is Result.Success -> existing.data
            is Result.Error -> return existing.asEmptyDataResult()
        }
        // Only ids that still exist locally and whose index actually moves.
        val changed = buildMap {
            orderedProjectIds.forEachIndexed { index, projectId ->
                val newIndex = index.toLong()
                if (current.containsKey(projectId) && current[projectId] != newIndex) {
                    put(projectId, newIndex)
                }
            }
        }
        if (changed.isEmpty()) return Result.Success(Unit)

        // One timestamp for both writes — reading the clock twice would stamp the local row and the
        // server row with different values for what is a single reorder.
        val updatedAt = timeProvider.nowInstant
        val localResult = localProjectDataSource.updateSortIndices(changed, updatedAt)
        if (localResult !is Result.Success) {
            return localResult.asEmptyDataResult()
        }

        return when (val remoteResult = remoteProjectDataSource.reorderProjects(changed, updatedAt  )) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> when {
                remoteResult.error.isTransient() -> {
                    // Local write already succeeded; only surface an error if queuing the sync fails.
                    val queued = enqueueReorderOperation()
                    if (queued is Result.Success) scheduleSync()
                    queued
                }
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    // CREATE/UPDATE task: same optimistic flow as projects.
    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> {
        val isCreate = when (val existing = localProjectDataSource.getTaskById(projectTask.projectTaskId)) {
            is Result.Success -> existing.data == null
            is Result.Error -> return existing.asEmptyDataResult()
        }
        val stamped = projectTask.copy(ownUpdatedAt = timeProvider.nowInstant)

        val localResult = localProjectDataSource.upsertProjectTask(stamped)
        if (localResult !is Result.Success) {
            return localResult.asEmptyDataResult()
        }

        val remoteResult = if (isCreate) {
            remoteProjectDataSource.postTaskByProjectId(stamped.parentProjectId, stamped)
        } else {
            remoteProjectDataSource.updateTaskByProjectId(stamped.parentProjectId, stamped)
        }
        return when (remoteResult) {
            is Result.Success -> {
                applicationScope.async {
                    localProjectDataSource.upsertProjectTask(remoteResult.data)
                }.await()
                Result.Success(Unit)
            }
            is Result.Error -> when {
                remoteResult.error == DataError.Remote.CONFLICT -> resolveTaskConflict(stamped)
                remoteResult.error.isTransient() -> {
                    // Local write already succeeded; only surface an error if queuing the sync fails.
                    val queued = enqueueTaskOperation(projectTask.projectTaskId, projectTask.parentProjectId, if (isCreate) PendingSyncOperation.OP_CREATE else PendingSyncOperation.OP_UPDATE)
                    if (queued is Result.Success) scheduleSync()
                    queued
                }
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    // DELETE project: local first, then remote; handle offline-create-then-delete (ghost) case.
    override suspend fun deleteProject(projectId: String): EmptyResult<DataError> {
        val hadPendingCreate = pendingSyncDataSource.hasPendingCreate(projectId).getOrDefault(false)

        val localResult = localProjectDataSource.deleteProject(projectId)
        if (localResult is Result.Error) {
            return localResult.asEmptyDataResult()
        }

        if (hadPendingCreate) {
            // Created offline and deleted before it ever reached the server → just drop the queue.
            pendingSyncDataSource.deleteOperationsByEntityId(projectId)
            return Result.Success(Unit)
        }

        val remoteResult = applicationScope.async {
            remoteProjectDataSource.deleteProject(projectId)
        }.await()
        return when (remoteResult) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> when {
                remoteResult.error.isTransient() -> {
                    // Local delete already succeeded; only surface an error if queuing the sync fails.
                    val queued = enqueueProjectOperation(projectId, PendingSyncOperation.OP_DELETE)
                    if (queued is Result.Success) scheduleSync()
                    queued
                }
                // Server already has no such project → the delete is effectively done.
                remoteResult.error == DataError.Remote.NOT_FOUND -> Result.Success(Unit)
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    override suspend fun deleteProjectTask(taskId: String) {

        val parentProjectId = localProjectDataSource.getTaskById(taskId).getOrDefault(null)?.parentProjectId
        val hadPendingCreate = pendingSyncDataSource.hasPendingCreate(taskId).getOrDefault(false)

        localProjectDataSource.deleteProjectTask(taskId)

        if (hadPendingCreate) {
            pendingSyncDataSource.deleteOperationsByEntityId(taskId)
            return
        }
        if (parentProjectId == null) return // cannot push a delete without the parent project id

        val remoteResult = applicationScope.async {
            remoteProjectDataSource.deleteTask(parentProjectId, taskId)
        }.await()
        if (remoteResult is Result.Error && remoteResult.error.isTransient()) {
            if (enqueueTaskOperation(taskId, parentProjectId,
                    PendingSyncOperation.OP_DELETE
                ) is Result.Success) scheduleSync()
        }
    }

    override suspend fun deleteAllProjects() {
        localProjectDataSource.deleteAllProjects()
    }

    override suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long) {
        localProjectDataSource.updateTaskDuration(taskId, newDurationMillis)
    }

    override fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> {
        return localProjectDataSource.getTaskWithIntervalsById(taskId)
    }

    // CREATE/UPDATE interval: local first (optimistic), then remote — same flow as projects/tasks.
    override suspend fun upsertTaskInterval(interval: TaskInterval) {
        val isCreate = localProjectDataSource.getIntervalById(interval.intervalId)
            .getOrDefault(null) == null

        if (localProjectDataSource.upsertTaskInterval(interval) !is Result.Success) return
        pushInterval(interval, isCreate)
    }

    override suspend fun getOpenIntervalByTaskId(taskId: String): TaskInterval? {
        return localProjectDataSource.getOpenIntervalByTaskId(taskId).getOrDefault(null)
    }

    override suspend fun startTask(taskId: String) {
        val opened = when (val started = localProjectDataSource.startTask(taskId)) {
            is Result.Success -> started.data
            is Result.Error -> return // local write failed; nothing to push
        }
        pushInterval(opened, isCreate = true)
    }

    override suspend fun stopTask(taskId: String) {
        val closed = when (val stopped = localProjectDataSource.stopTask(taskId)) {
            is Result.Success -> stopped.data
            is Result.Error -> return
        }
        // The interval carries the measured span, the task carries the accumulated total and the
        // cleared timer flag; both have to reach the server. The interval goes first so that a
        // failure pushing the task cannot strand it.
        if (closed != null) pushInterval(closed, isCreate = false)
        val task = localProjectDataSource.getTaskById(taskId).getOrDefault(null)
        if (task != null) upsertProjectTask(task)
    }

    // DELETE interval: local first, then remote, with the same offline-create-then-delete (ghost)
    // handling as tasks — an interval that never reached the server just drops out of the queue.
    override suspend fun deleteTaskInterval(intervalId: String) {
        val interval = localProjectDataSource.getIntervalById(intervalId)
            .getOrDefault(null) ?: return // already gone locally
        val hadPendingCreate = pendingSyncDataSource.hasPendingCreate(intervalId).getOrDefault(false)

        if (localProjectDataSource.deleteTaskInterval(intervalId) !is Result.Success) return

        if (hadPendingCreate) {
            pendingSyncDataSource.deleteOperationsByEntityId(intervalId)
            return
        }

        val taskId = interval.parentTaskId

        val remoteResult = applicationScope.async {
            remoteProjectDataSource.deleteInterval(interval.parentProjectId, taskId, intervalId)
        }.await()
        if (remoteResult is Result.Error && remoteResult.error.isTransient()) {
            if (enqueueIntervalOperation(intervalId, taskId, PendingSyncOperation.OP_DELETE) is Result.Success) scheduleSync()
        }
        // A NOT_FOUND here means the server already has no such interval — the delete is done.
    }

    /**
     * Pushes one interval, queueing it whenever the push cannot succeed yet.
     *
     * Two failures get their own handling:
     * - `CONFLICT` on a create means the POST already landed and only its response was lost, so the
     *   same interval is retried as an update rather than resolved by last-write-wins.
     * - `NOT_FOUND` means the parent task does not exist server-side yet, which happens whenever
     *   the task was created offline and the timer ran before the queue drained. That must be
     *   queued rather than dropped: the queue drains FIFO, so the task's own CREATE is pushed
     *   first and the retry then succeeds. Dropping here would silently lose tracked time in
     *   exactly the offline case this feature exists for.
     */
    private suspend fun pushInterval(interval: TaskInterval, isCreate: Boolean) {
        val taskId = interval.parentTaskId
        val projectId = interval.parentProjectId

        val remoteResult = if (isCreate) {
            remoteProjectDataSource.postInterval(projectId, taskId, interval)
        } else {
            remoteProjectDataSource.updateInterval(projectId, taskId, interval)
        }

        when (remoteResult) {
            // Server is canonical on the happy path, exactly like projects and tasks.
            is Result.Success -> localProjectDataSource.upsertTaskInterval(remoteResult.data)
            is Result.Error -> when {
                isCreate && remoteResult.error == DataError.Remote.CONFLICT ->
                    resolveIntervalConflict(projectId, taskId, interval)
                remoteResult.error == DataError.Remote.NOT_FOUND || remoteResult.error.isTransient() -> {
                    val operation = if (isCreate) PendingSyncOperation.OP_CREATE else PendingSyncOperation.OP_UPDATE
                    if (enqueueIntervalOperation(interval.intervalId, taskId, operation) is Result.Success) {
                        scheduleSync()
                    }
                }
                else -> Unit // permanent error — the local row stands, nothing left to try
            }
        }
    }

    /** A duplicate create means the row is already on the server: push local state as an update. */
    private suspend fun resolveIntervalConflict(projectId: String, taskId: String, interval: TaskInterval) {
        when (val updated = remoteProjectDataSource.updateInterval(projectId, taskId, interval)) {
            is Result.Success -> localProjectDataSource.upsertTaskInterval(updated.data)
            is Result.Error -> if (updated.error.isTransient()) {
                if (enqueueIntervalOperation(interval.intervalId, taskId,
                        PendingSyncOperation.OP_UPDATE
                    ) is Result.Success) {
                    scheduleSync()
                }
            }
        }
    }

    /**
     * Resolves an interval route's project id from the task alone.
     *
     * Intervals carry their own `parentProjectId`, so this is only needed for a queued DELETE: by
     * the time that op drains, the local interval row is gone and the task id stored on the queue
     * entry is all that is left to go on.
     */
    private suspend fun parentProjectIdOf(taskId: String): String? =
        localProjectDataSource.getTaskById(taskId).getOrDefault(null)?.parentProjectId

    // Title edits must reach the server too. Route through the offline-first upsert so the change
    // is pushed remotely (and queued for sync when offline) instead of staying local-only.
    override suspend fun updateTaskTitle(taskId: String, title: String) {
        val task = localProjectDataSource.getTaskById(taskId).getOrDefault(null) ?: return
        upsertProjectTask(task.copy(title = title))
    }

    // ---------------------------------------------------------------------------------------------
    // Pending-sync queue draining
    // ---------------------------------------------------------------------------------------------

    override suspend fun syncPendingOperations() = withContext(Dispatchers.Default) {
        // Drain FIFO so a CREATE is always pushed before a later UPDATE on the same entity.
        pendingSyncDataSource.getPendingOperations().getOrDefault(emptyList()).forEach { op ->
            when (runSyncOperation(op)) {
                SyncOutcome.SUCCESS, SyncOutcome.DROP -> pendingSyncDataSource.deleteOperation(op.operationId)
                SyncOutcome.RETRY -> Unit // leave queued for the next attempt
            }
        }
    }

    private suspend fun runSyncOperation(op: PendingSyncOperation): SyncOutcome {
        return when (op.entityType) {
            PendingSyncOperation.ENTITY_PROJECT -> when (op.operationType) {
                PendingSyncOperation.OP_CREATE, PendingSyncOperation.OP_UPDATE -> {
                    val project = when (val r = localProjectDataSource.getProjectById(op.entityId)) {
                        is Result.Success -> r.data ?: return SyncOutcome.DROP
                        is Result.Error -> return SyncOutcome.RETRY
                    }
                    val result = if (op.operationType == PendingSyncOperation.OP_CREATE) {
                        remoteProjectDataSource.postProject(project)
                    } else {
                        remoteProjectDataSource.updateProject(project)
                    }
                    result.toSyncOutcome(
                        onSuccess = { localProjectDataSource.upsertProject(it.withLocalSortIndexFallback(project)) },
                        onConflict = { resolveProjectConflict(project) }
                    )
                }
                PendingSyncOperation.OP_DELETE -> remoteProjectDataSource.deleteProject(op.entityId).toSyncOutcome()
                else -> SyncOutcome.DROP
            }
            PendingSyncOperation.ENTITY_TASK -> when (op.operationType) {
                PendingSyncOperation.OP_CREATE, PendingSyncOperation.OP_UPDATE -> {
                    val task = when (val r = localProjectDataSource.getTaskById(op.entityId)) {
                        is Result.Success -> r.data ?: return SyncOutcome.DROP
                        is Result.Error -> return SyncOutcome.RETRY
                    }
                    val result = if (op.operationType == PendingSyncOperation.OP_CREATE) {
                        remoteProjectDataSource.postTaskByProjectId(task.parentProjectId, task)
                    } else {
                        remoteProjectDataSource.updateTaskByProjectId(task.parentProjectId, task)
                    }
                    result.toSyncOutcome(
                        onSuccess = { localProjectDataSource.upsertProjectTask(it) },
                        onConflict = { resolveTaskConflict(task) }
                    )
                }
                PendingSyncOperation.OP_DELETE -> {
                    val parentProjectId = op.parentEntityId ?: return SyncOutcome.DROP
                    remoteProjectDataSource.deleteTask(parentProjectId, op.entityId).toSyncOutcome()
                }
                else -> SyncOutcome.DROP
            }
            PendingSyncOperation.ENTITY_INTERVAL -> when (op.operationType) {
                PendingSyncOperation.OP_CREATE, PendingSyncOperation.OP_UPDATE -> {
                    val interval = when (val r = localProjectDataSource.getIntervalById(op.entityId)) {
                        is Result.Success -> r.data ?: return SyncOutcome.DROP // deleted meanwhile
                        is Result.Error -> return SyncOutcome.RETRY
                    }
                    val taskId = interval.parentTaskId
                    val projectId = interval.parentProjectId
                    val result = if (op.operationType == PendingSyncOperation.OP_CREATE) {
                        remoteProjectDataSource.postInterval(projectId, taskId, interval)
                    } else {
                        remoteProjectDataSource.updateInterval(projectId, taskId, interval)
                    }
                    result.toSyncOutcome(
                        onSuccess = { localProjectDataSource.upsertTaskInterval(it) },
                        onConflict = { resolveIntervalConflict(projectId, taskId, interval) }
                    )
                }
                PendingSyncOperation.OP_DELETE -> {
                    val taskId = op.parentEntityId ?: return SyncOutcome.DROP
                    val projectId = parentProjectIdOf(taskId) ?: return SyncOutcome.DROP
                    remoteProjectDataSource.deleteInterval(projectId, taskId, op.entityId).toSyncOutcome()
                }
                else -> SyncOutcome.DROP
            }
            // The queued row is just a marker: the order itself is rebuilt from current local state,
            // so projects deleted meanwhile drop out and repeated offline reorders collapse into one push.
            PendingSyncOperation.ENTITY_PROJECT_ORDER -> {
                val indices = when (val r = localProjectDataSource.getSortIndices()) {
                    is Result.Success -> r.data.mapNotNull { (id, index) -> index?.let { id to it } }.toMap()
                    is Result.Error -> return SyncOutcome.RETRY
                }
                if (indices.isEmpty()) return SyncOutcome.DROP
                remoteProjectDataSource.reorderProjects(indices, timeProvider.nowInstant).toSyncOutcome()
            }
            else -> SyncOutcome.DROP
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Conflict resolution (last-write-wins on the newer updatedAt)
    // ---------------------------------------------------------------------------------------------

    private suspend fun resolveProjectConflict(local: Project): EmptyResult<DataError> {
        val server = when (val r = remoteProjectDataSource.getProjects()) {
            is Result.Success -> r.data.find { it.projectId == local.projectId }
            is Result.Error -> return r.asEmptyDataResult()
        } ?: return remoteProjectDataSource.postProject(local).asEmptyDataResult() // server has none → push local

        // Last-write-wins compares this project row against the same row on the server, so it must
        // read the row's own stamp — never the lastUpdatedAt roll-up over its tasks.
        return if (local.ownUpdatedAt != null && (server.ownUpdatedAt == null || local.ownUpdatedAt > server.ownUpdatedAt)) {
            when (val pushed = remoteProjectDataSource.updateProject(local)) {
                is Result.Success -> {
                    val merged = pushed.data.withLocalSortIndexFallback(local)
                    applicationScope.async { localProjectDataSource.upsertProject(merged) }.await()
                    Result.Success(Unit)
                }
                is Result.Error -> pushed.asEmptyDataResult()
            }
        } else {
            // Server wins on freshness, but keep the local sortIndex when the server has none.
            val merged = server.withLocalSortIndexFallback(local)
            applicationScope.async { localProjectDataSource.upsertProject(merged) }.await()
            Result.Success(Unit)
        }
    }

    /**
     * Keeps the locally known order when the server has no sortIndex of its own. Without this, any
     * ordinary edit (pin, rename, archive) would silently wipe the order the user just dragged.
     */
    private fun Project.withLocalSortIndexFallback(local: Project): Project =
        if (sortIndex != null) this else copy(sortIndex = local.sortIndex)

    private suspend fun resolveTaskConflict(local: ProjectTask): EmptyResult<DataError> {
        val server = when (val r = remoteProjectDataSource.getTasksByProjectId(local.parentProjectId)) {
            is Result.Success -> r.data.find { it.projectTaskId == local.projectTaskId }
            is Result.Error -> return r.asEmptyDataResult()
        } ?: return remoteProjectDataSource.postTaskByProjectId(local.parentProjectId, local).asEmptyDataResult()

        return if ((local.ownUpdatedAt != null) && ((server.ownUpdatedAt == null) || (local.ownUpdatedAt > server.ownUpdatedAt))) {
            when (val pushed = remoteProjectDataSource.updateTaskByProjectId(local.parentProjectId, local)) {
                is Result.Success -> {
                    applicationScope.async { localProjectDataSource.upsertProjectTask(pushed.data) }.await()
                    Result.Success(Unit)
                }
                is Result.Error -> pushed.asEmptyDataResult()
            }
        } else {
            applicationScope.async { localProjectDataSource.upsertProjectTask(server) }.await()
            Result.Success(Unit)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Queue helpers
    // ---------------------------------------------------------------------------------------------

    private suspend fun enqueueProjectOperation(projectId: String, operationType: String): EmptyResult<DataError> {
        return enqueueOperation(projectId,
            PendingSyncOperation.ENTITY_PROJECT, operationType, parentEntityId = null)
    }

    // One row for the whole order, not one per moved project. enqueueDeduped's OP_UPDATE rule then
    // collapses further offline reorders into this same row.
    private suspend fun enqueueReorderOperation(): EmptyResult<DataError> {
        return enqueueOperation(
            PendingSyncOperation.PROJECT_ORDER_ENTITY_ID,
            PendingSyncOperation.ENTITY_PROJECT_ORDER,
            PendingSyncOperation.OP_UPDATE, parentEntityId = null)
    }

    private suspend fun enqueueTaskOperation(taskId: String, parentProjectId: String, operationType: String): EmptyResult<DataError> {
        // parentEntityId is only needed for DELETE (the task row is gone by drain time).
        val parent = if (operationType == PendingSyncOperation.OP_DELETE) parentProjectId else null
        return enqueueOperation(taskId, PendingSyncOperation.ENTITY_TASK, operationType, parent)
    }

    // parentEntityId always carries the parent *task* id here, not the project id: unlike a task
    // DELETE, the interval routes need both ids on every operation, and the project is looked up
    // from the task when the op drains.
    private suspend fun enqueueIntervalOperation(intervalId: String, taskId: String, operationType: String): EmptyResult<DataError> {
        return enqueueOperation(intervalId,
            PendingSyncOperation.ENTITY_INTERVAL, operationType, parentEntityId = taskId)
    }

    private suspend fun enqueueOperation(
        entityId: String,
        entityType: String,
        operationType: String,
        parentEntityId: String?
    ): EmptyResult<DataError> = pendingSyncDataSource.enqueue(
        entityId = entityId,
        entityType = entityType,
        operationType = operationType,
        parentEntityId = parentEntityId,
        createdAt = timeProvider.nowInstant
    )

    private suspend fun scheduleSync() {
        applicationScope.launch { syncScheduler.schedulePeriodicSync() }.join()
    }
}
