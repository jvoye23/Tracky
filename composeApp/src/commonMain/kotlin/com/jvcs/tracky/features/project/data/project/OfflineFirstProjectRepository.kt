package com.jvcs.tracky.features.project.data.project

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
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.project.LocalProjectDataSource
import com.jvcs.tracky.features.project.domain.project.LocalProjectOrganizationDataSource
import com.jvcs.tracky.features.project.domain.project.LocalServerTreeDataSource
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.project.RemoteProjectDataSource
import com.jvcs.tracky.features.project.domain.project.sortedByCustomOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.time.Instant

class OfflineFirstProjectRepository(
    private val localProjectDataSource: LocalProjectDataSource,
    private val localProjectOrganizationDataSource: LocalProjectOrganizationDataSource,
    private val localServerTreeDataSource: LocalServerTreeDataSource,
    private val remoteProjectDataSource: RemoteProjectDataSource,
    private val pendingSyncDataSource: PendingSyncDataSource,
    private val syncScheduler: SyncScheduler,
    private val applicationScope: CoroutineScope,
    private val timeProvider: TimeProvider,
) : ProjectRepository {

    override suspend fun fetchProjects(): EmptyResult<DataError> =
        when (val remoteResult = remoteProjectDataSource.getProjects()) {
            is Result.Error -> {
                remoteResult.asEmptyDataResult()
            }

            is Result.Success -> {
                applicationScope
                    .async {
                        localServerTreeDataSource.upsertProjects(remoteResult.data).asEmptyDataResult()
                    }.await()
            }
        }

    override fun getProjects(): Flow<List<Project>> = localProjectDataSource.getProjects()

    override fun getActiveProjects(): Flow<List<Project>> = localProjectDataSource.getActiveProjects()

    override suspend fun getProjectById(projectId: String): Project? =
        localProjectDataSource.getProjectById(projectId).getOrDefault(null)

    // Local only: Room is the source of truth, and every remote change reaches it through a sync
    // pull, so the stream picks those up without a call of its own.
    override fun observeProjectById(projectId: String): Flow<Project?> =
        localProjectDataSource.observeProjectById(projectId)

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? =
        localProjectDataSource.getProjectWithTasksByProjectId(projectId).getOrDefault(null)

    // Local only, for the same reason as observeProjectById: Room is the source of truth and a
    // sync pull is what puts another device's rows into it.
    override fun observeProjectWithTaskTreeById(projectId: String): Flow<Project?> =
        localProjectDataSource.observeProjectWithTaskTreeById(projectId)

    // CREATE/UPDATE project: local first (optimistic), then remote; on transient failure → queue.
    override suspend fun upsertProject(project: Project): EmptyResult<DataError> {
        val isCreate =
            when (val existing = localProjectDataSource.getProjectById(project.projectId)) {
                is Result.Success -> existing.data == null
                is Result.Error -> return existing.asEmptyDataResult()
            }
        val stamped = project.copy(ownUpdatedAt = timeProvider.nowInstant)

        val localResult = localProjectDataSource.upsertProject(stamped)
        if (localResult !is Result.Success) {
            return localResult.asEmptyDataResult()
        }

        val remoteResult =
            if (isCreate) {
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

            is Result.Error -> {
                when {
                    remoteResult.error == DataError.Remote.CONFLICT -> {
                        resolveProjectConflict(stamped)
                    }

                    remoteResult.error.isTransient() -> {
                        // Local write already succeeded; only surface an error if queuing the sync fails.
                        val queued =
                            enqueueProjectOperation(
                                project.projectId,
                                if (isCreate) PendingSyncOperation.OP_CREATE else PendingSyncOperation.OP_UPDATE,
                            )
                        if (queued is Result.Success) scheduleSync()
                        queued
                    }

                    else -> {
                        remoteResult.asEmptyDataResult()
                    }
                }
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

        val remoteResult =
            applicationScope
                .async {
                    remoteProjectDataSource.deleteProject(projectId)
                }.await()
        return when (remoteResult) {
            is Result.Success -> {
                Result.Success(Unit)
            }

            is Result.Error -> {
                when {
                    remoteResult.error.isTransient() -> {
                        // Local delete already succeeded; only surface an error if queuing the sync fails.
                        val queued = enqueueProjectOperation(projectId, PendingSyncOperation.OP_DELETE)
                        if (queued is Result.Success) scheduleSync()
                        queued
                    }

                    // Server already has no such project → the delete is effectively done.
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

    override suspend fun deleteAllProjects() {
        localProjectDataSource.deleteAllProjects()
    }

    // ---------------------------------------------------------------------------------------------
    // Pending-sync queue draining
    // ---------------------------------------------------------------------------------------------

    override suspend fun syncPendingProjects(): EmptyResult<DataError> =
        pendingSyncDataSource.drain(matching = {
            it.entityType == PendingSyncOperation.ENTITY_PROJECT ||
                it.entityType == PendingSyncOperation.ENTITY_PROJECT_ORDER
        }) { runProjectOperation(it) }

    private suspend fun runProjectOperation(op: PendingSyncOperation): SyncOutcome =
        when (op.entityType) {
            PendingSyncOperation.ENTITY_PROJECT -> {
                when (op.operationType) {
                    PendingSyncOperation.OP_CREATE, PendingSyncOperation.OP_UPDATE -> {
                        localProjectDataSource.getProjectById(op.entityId).pushQueuedRow { pushProject(op, it) }
                    }

                    PendingSyncOperation.OP_DELETE -> {
                        remoteProjectDataSource.deleteProject(op.entityId).toSyncOutcome()
                    }

                    else -> {
                        SyncOutcome.DROP
                    }
                }
            }

            PendingSyncOperation.ENTITY_PROJECT_ORDER -> {
                pushProjectOrder()
            }

            else -> {
                SyncOutcome.DROP
            }
        }

    private suspend fun pushProject(op: PendingSyncOperation, project: Project): SyncOutcome {
        val result =
            if (op.operationType == PendingSyncOperation.OP_CREATE) {
                remoteProjectDataSource.postProject(project)
            } else {
                remoteProjectDataSource.updateProject(project)
            }
        return result.toSyncOutcome(
            onSuccess = {
                localProjectDataSource.upsertProject(
                    it.withLocalSortIndexFallback(project),
                )
            },
            onConflict = { resolveProjectConflict(project) },
        )
    }

    // The queued row is just a marker: the order itself is rebuilt from current local state,
    // so projects deleted meanwhile drop out and repeated offline reorders collapse into one push.
    private suspend fun pushProjectOrder(): SyncOutcome {
        val indices =
            when (val result = localProjectOrganizationDataSource.getSortIndices()) {
                is Result.Success -> result.data.mapNotNull { (id, index) -> index?.let { id to it } }.toMap()
                is Result.Error -> return SyncOutcome.RETRY
            }
        if (indices.isEmpty()) return SyncOutcome.DROP
        return remoteProjectDataSource.reorderProjects(indices, timeProvider.nowInstant).toSyncOutcome()
    }

    // ---------------------------------------------------------------------------------------------
    // Conflict resolution (last-write-wins on the newer updatedAt)
    // ---------------------------------------------------------------------------------------------

    private suspend fun resolveProjectConflict(local: Project): EmptyResult<DataError> {
        val server =
            when (val result = remoteProjectDataSource.getProjects()) {
                is Result.Success -> result.data.find { it.projectId == local.projectId }
                is Result.Error -> return result.asEmptyDataResult()
            } ?: return remoteProjectDataSource.postProject(local).asEmptyDataResult() // server has none → push local

        // Last-write-wins compares this project row against the same row on the server, so it must
        // read the row's own stamp — never the lastUpdatedAt roll-up over its tasks.
        return if (local.ownUpdatedAt != null &&
            (server.ownUpdatedAt == null || local.ownUpdatedAt > server.ownUpdatedAt)
        ) {
            when (val pushed = remoteProjectDataSource.updateProject(local)) {
                is Result.Success -> {
                    val merged = pushed.data.withLocalSortIndexFallback(local)
                    applicationScope.async { localProjectDataSource.upsertProject(merged) }.await()
                    Result.Success(Unit)
                }

                is Result.Error -> {
                    pushed.asEmptyDataResult()
                }
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

    // ---------------------------------------------------------------------------------------------
    // Queue helpers
    // ---------------------------------------------------------------------------------------------

    private suspend fun enqueueProjectOperation(projectId: String, operationType: String): EmptyResult<DataError> =
        pendingSyncDataSource.enqueue(
            entityId = projectId,
            entityType = PendingSyncOperation.ENTITY_PROJECT,
            operationType = operationType,
            parentEntityId = null,
            createdAt = timeProvider.nowInstant,
        )

    private suspend fun scheduleSync() {
        applicationScope.launch { syncScheduler.schedulePeriodicSync() }.join()
    }
}
