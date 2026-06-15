@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.database.dao.PendingSyncDao
import com.jvcs.tracky.core.database.entity.PendingSyncEntity
import com.jvcs.tracky.core.database.entity.PendingSyncEntity.Companion.ENTITY_PROJECT
import com.jvcs.tracky.core.database.entity.PendingSyncEntity.Companion.ENTITY_TASK
import com.jvcs.tracky.core.database.entity.PendingSyncEntity.Companion.OP_CREATE
import com.jvcs.tracky.core.database.entity.PendingSyncEntity.Companion.OP_DELETE
import com.jvcs.tracky.core.database.entity.PendingSyncEntity.Companion.OP_UPDATE
import com.jvcs.tracky.core.domain.RemoteProjectDataSource
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval
import com.jvcs.tracky.core.domain.sync.SyncRepository
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.asEmptyDataResult
import com.jvcs.tracky.features.project_tracker.domain.LocalProjectDataSource
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class OfflineFirstProjectRepository(
    private val localProjectDataSource: LocalProjectDataSource,
    private val remoteProjectDataSource: RemoteProjectDataSource,
    private val pendingSyncDao: PendingSyncDao,
    private val syncScheduler: SyncScheduler,
    private val applicationScope: CoroutineScope
): ProjectRepository, SyncRepository {

    // PULL: network → Room. The Room Flow the UI observes emits automatically.
    override suspend fun fetchProjects(): EmptyResult<DataError> {
        return when(val result = remoteProjectDataSource.getProjects()) {
            is Result.Error -> result.asEmptyDataResult()
            is Result.Success -> {
                applicationScope.async {
                    localProjectDataSource.upsertProjects(result.data).asEmptyDataResult()
                }.await()
            }
        }
    }

    override fun getProjects(): Flow<List<Project>> {
        return localProjectDataSource.getProjects()
    }

    override suspend fun getProjectById(projectId: String): Project? {
        return localProjectDataSource.getProjectById(projectId)
    }

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? {
        return localProjectDataSource.getProjectWithTasksByProjectId(projectId)
    }

    // CREATE/UPDATE project: local first (optimistic), then remote; on transient failure → queue.
    override suspend fun upsertProject(project: Project): EmptyResult<DataError> {
        val isCreate = when (val existing = dbResult { localProjectDataSource.getProjectById(project.projectId) }) {
            is Result.Success -> existing.data == null
            is Result.Error -> return existing.asEmptyDataResult()
        }
        val stamped = project.copy(updatedAt = Clock.System.now())

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
                // Server is canonical (server-wins on the happy path).
                localProjectDataSource.upsertProject(remoteResult.data)
                Result.Success(Unit)
            }
            is Result.Error -> when {
                remoteResult.error == DataError.Network.CONFLICT -> resolveProjectConflict(stamped)
                remoteResult.error.isTransient() -> {
                    // Local write already succeeded; only surface an error if queuing the sync fails.
                    val queued = enqueueProjectOperation(project.projectId, if (isCreate) OP_CREATE else OP_UPDATE)
                    if (queued is Result.Success) scheduleSync()
                    queued
                }
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    // CREATE/UPDATE task: same optimistic flow as projects.
    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> {
        val isCreate = when (val existing = dbResult { localProjectDataSource.getTaskWithIntervalsById(projectTask.projectTaskId).first() }) {
            is Result.Success -> existing.data == null
            is Result.Error -> return existing.asEmptyDataResult()
        }
        val stamped = projectTask.copy(updatedAt = Clock.System.now())

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
                remoteResult.error == DataError.Network.CONFLICT -> resolveTaskConflict(stamped)
                remoteResult.error.isTransient() -> {
                    // Local write already succeeded; only surface an error if queuing the sync fails.
                    val queued = enqueueTaskOperation(projectTask.projectTaskId, projectTask.parentProjectId, if (isCreate) OP_CREATE else OP_UPDATE)
                    if (queued is Result.Success) scheduleSync()
                    queued
                }
                else -> remoteResult.asEmptyDataResult()
            }
        }
    }

    // DELETE project: local first, then remote; handle offline-create-then-delete (ghost) case.
    override suspend fun deleteProject(projectId: String) {
        val hadPendingCreate = dbResult {
            pendingSyncDao.getOperationsByEntityId(projectId).any { it.operationType == OP_CREATE }
        }.getOrDefault(false)

        dbResult { localProjectDataSource.deleteProject(projectId) }

        if (hadPendingCreate) {
            // Created offline and deleted before it ever reached the server → just drop the queue.
            dbResult { pendingSyncDao.deleteOperationsByEntityId(projectId) }
            return
        }

        val remoteResult = applicationScope.async {
            remoteProjectDataSource.deleteProject(projectId)
        }.await()
        if (remoteResult is Result.Error && remoteResult.error.isTransient()) {
            if (enqueueProjectOperation(projectId, OP_DELETE) is Result.Success) scheduleSync()
        }
    }

    override suspend fun deleteProjectTask(taskId: String) {
        val parentProjectId = dbResult {
            localProjectDataSource.getTaskWithIntervalsById(taskId).first()?.parentProjectId
        }.getOrDefault(null)
        val hadPendingCreate = dbResult {
            pendingSyncDao.getOperationsByEntityId(taskId).any { it.operationType == OP_CREATE }
        }.getOrDefault(false)

        dbResult { localProjectDataSource.deleteProjectTask(taskId) }

        if (hadPendingCreate) {
            dbResult { pendingSyncDao.deleteOperationsByEntityId(taskId) }
            return
        }
        if (parentProjectId == null) return // cannot push a delete without the parent project id

        val remoteResult = applicationScope.async {
            remoteProjectDataSource.deleteTask(parentProjectId, taskId)
        }.await()
        if (remoteResult is Result.Error && remoteResult.error.isTransient()) {
            if (enqueueTaskOperation(taskId, parentProjectId, OP_DELETE) is Result.Success) scheduleSync()
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

    override suspend fun upsertTaskInterval(interval: TaskInterval) {
        localProjectDataSource.upsertTaskInterval(interval)
    }

    override suspend fun getOpenIntervalByTaskId(taskId: String): TaskInterval? {
        return localProjectDataSource.getOpenIntervalByTaskId(taskId)
    }

    override suspend fun startTask(taskId: String) {
        localProjectDataSource.startTask(taskId)
    }

    override suspend fun stopTask(taskId: String) {
        localProjectDataSource.stopTask(taskId)
    }

    // Title edits must reach the server too. Route through the offline-first upsert so the change
    // is pushed remotely (and queued for sync when offline) instead of staying local-only.
    override suspend fun updateTaskTitle(taskId: String, title: String) {
        val task = dbResult {
            localProjectDataSource.getTaskWithIntervalsById(taskId).first()
        }.getOrDefault(null) ?: return
        upsertProjectTask(task.copy(title = title))
    }

    // ---------------------------------------------------------------------------------------------
    // Pending-sync queue draining
    // ---------------------------------------------------------------------------------------------

    override suspend fun syncPendingOperations() = withContext(Dispatchers.Default) {
        // Drain FIFO so a CREATE is always pushed before a later UPDATE on the same entity.
        pendingSyncDao.getAllPendingOperations().forEach { op ->
            when (runSyncOperation(op)) {
                SyncOutcome.SUCCESS, SyncOutcome.DROP -> pendingSyncDao.deleteOperation(op.operationId)
                SyncOutcome.RETRY -> Unit // leave queued for the next attempt
            }
        }
    }

    private suspend fun runSyncOperation(op: PendingSyncEntity): SyncOutcome {
        return when (op.entityType) {
            ENTITY_PROJECT -> when (op.operationType) {
                OP_CREATE, OP_UPDATE -> {
                    val project = when (val r = dbResult { localProjectDataSource.getProjectById(op.entityId) }) {
                        is Result.Success -> r.data ?: return SyncOutcome.DROP
                        is Result.Error -> return SyncOutcome.RETRY
                    }
                    val result = if (op.operationType == OP_CREATE) {
                        remoteProjectDataSource.postProject(project)
                    } else {
                        remoteProjectDataSource.updateProject(project)
                    }
                    result.toSyncOutcome(
                        onSuccess = { localProjectDataSource.upsertProject(it) },
                        onConflict = { resolveProjectConflict(project) }
                    )
                }
                OP_DELETE -> remoteProjectDataSource.deleteProject(op.entityId).toSyncOutcome()
                else -> SyncOutcome.DROP
            }
            ENTITY_TASK -> when (op.operationType) {
                OP_CREATE, OP_UPDATE -> {
                    val task = localProjectDataSource.getTaskWithIntervalsById(op.entityId).first()
                        ?: return SyncOutcome.DROP
                    val result = if (op.operationType == OP_CREATE) {
                        remoteProjectDataSource.postTaskByProjectId(task.parentProjectId, task)
                    } else {
                        remoteProjectDataSource.updateTaskByProjectId(task.parentProjectId, task)
                    }
                    result.toSyncOutcome(
                        onSuccess = { localProjectDataSource.upsertProjectTask(it) },
                        onConflict = { resolveTaskConflict(task) }
                    )
                }
                OP_DELETE -> {
                    val parentProjectId = op.parentEntityId ?: return SyncOutcome.DROP
                    remoteProjectDataSource.deleteTask(parentProjectId, op.entityId).toSyncOutcome()
                }
                else -> SyncOutcome.DROP
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

        return if (local.updatedAt != null && (server.updatedAt == null || local.updatedAt > server.updatedAt)) {
            when (val pushed = remoteProjectDataSource.updateProject(local)) {
                is Result.Success -> {
                    applicationScope.async { localProjectDataSource.upsertProject(pushed.data) }.await()
                    Result.Success(Unit)
                }
                is Result.Error -> pushed.asEmptyDataResult()
            }
        } else {
            applicationScope.async { localProjectDataSource.upsertProject(server) }.await()
            Result.Success(Unit)
        }
    }

    private suspend fun resolveTaskConflict(local: ProjectTask): EmptyResult<DataError> {
        val server = when (val r = remoteProjectDataSource.getTasksByProjectId(local.parentProjectId)) {
            is Result.Success -> r.data.find { it.projectTaskId == local.projectTaskId }
            is Result.Error -> return r.asEmptyDataResult()
        } ?: return remoteProjectDataSource.postTaskByProjectId(local.parentProjectId, local).asEmptyDataResult()

        return if ((local.updatedAt != null) && ((server.updatedAt == null) || (local.updatedAt > server.updatedAt))) {
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
        return enqueueOperation(projectId, ENTITY_PROJECT, operationType, parentEntityId = null)
    }

    private suspend fun enqueueTaskOperation(taskId: String, parentProjectId: String, operationType: String): EmptyResult<DataError> {
        // parentEntityId is only needed for DELETE (the task row is gone by drain time).
        val parent = if (operationType == OP_DELETE) parentProjectId else null
        return enqueueOperation(taskId, ENTITY_TASK, operationType, parent)
    }

    private suspend fun enqueueOperation(
        entityId: String,
        entityType: String,
        operationType: String,
        parentEntityId: String?
    ): EmptyResult<DataError> = dbResult {
        pendingSyncDao.enqueueDeduped(
            PendingSyncEntity(
                operationId = Uuid.random().toString(),
                entityId = entityId,
                entityType = entityType,
                operationType = operationType,
                createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                parentEntityId = parentEntityId
            )
        )
    }.asEmptyDataResult()

    /**
     * Runs a Room call, converting any failure (e.g. a corrupt/unavailable database) into a
     * recoverable [DataError.Local] instead of letting it crash the calling coroutine.
     */
    private suspend fun <T> dbResult(block: suspend () -> T): Result<T, DataError.Local> {
        return try {
            Result.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    private fun <T, E : com.jvcs.tracky.core.domain.util.Error> Result<T, E>.getOrDefault(default: T): T = when (this) {
        is Result.Success -> data
        is Result.Error -> default
    }

    private suspend fun scheduleSync() {
        applicationScope.launch { syncScheduler.schedulePeriodicSync() }.join()
    }

    private enum class SyncOutcome { SUCCESS, RETRY, DROP }

    private suspend fun <T> Result<T, DataError.Network>.toSyncOutcome(
        onSuccess: suspend (T) -> Unit,
        onConflict: suspend () -> Unit
    ): SyncOutcome = when (this) {
        is Result.Success -> { onSuccess(data); SyncOutcome.SUCCESS }
        is Result.Error -> when {
            error == DataError.Network.CONFLICT -> { onConflict(); SyncOutcome.SUCCESS }
            error.isTransient() -> SyncOutcome.RETRY
            else -> SyncOutcome.DROP // permanent error (e.g. NOT_FOUND for a delete) — give up
        }
    }

    private fun EmptyResult<DataError.Network>.toSyncOutcome(): SyncOutcome = when (this) {
        is Result.Success -> SyncOutcome.SUCCESS
        is Result.Error -> if (error.isTransient()) SyncOutcome.RETRY else SyncOutcome.DROP
    }

    private fun DataError.Network.isTransient(): Boolean = when (this) {
        DataError.Network.NO_INTERNET,
        DataError.Network.REQUEST_TIMEOUT,
        DataError.Network.SERVER_ERROR,
        DataError.Network.SERVICE_UNAVAILABLE,
        DataError.Network.TOO_MANY_REQUESTS,
        DataError.Network.UNKNOWN -> true
        else -> false
    }
}
