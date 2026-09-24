package com.jvcs.tracky.features.project.data.project

import com.jvcs.tracky.core.domain.sync.PendingSyncDataSource
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.asEmptyDataResult
import com.jvcs.tracky.core.domain.util.isTransient
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.project.LocalProjectDataSource
import com.jvcs.tracky.features.project.domain.project.LocalProjectOrganizationDataSource
import com.jvcs.tracky.features.project.domain.project.ProjectOrganizationRepository
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

/**
 * Archive, trash, pin and reorder. Every flag flip is an ordinary project write, so it goes
 * through [ProjectRepository.upsertProject] and gets the same online push and offline queue as
 * any other edit. The order is its own queued operation; ProjectRepository's drain pushes it.
 */
class OfflineFirstProjectOrganizationRepository(
    private val projectRepository: ProjectRepository,
    private val localProjectDataSource: LocalProjectDataSource,
    private val localProjectOrganizationDataSource: LocalProjectOrganizationDataSource,
    private val remoteProjectDataSource: RemoteProjectDataSource,
    private val pendingSyncDataSource: PendingSyncDataSource,
    private val syncScheduler: SyncScheduler,
    private val applicationScope: CoroutineScope,
    private val timeProvider: TimeProvider,
) : ProjectOrganizationRepository {

    override fun getArchivedProjects(): Flow<List<Project>> = localProjectOrganizationDataSource.getArchivedProjects()

    override fun getTrashedProjects(): Flow<List<Project>> = localProjectOrganizationDataSource.getTrashedProjects()

    // ARCHIVE/UNARCHIVE: flip the flag and route through the offline-first upsert so the change is
    // pushed to the server immediately when online (and only queued for sync when offline), exactly
    // like every other write.
    override suspend fun setProjectArchived(projectId: String, isArchived: Boolean): EmptyResult<DataError> {
        val project =
            when (val existing = localProjectDataSource.getProjectById(projectId)) {
                is Result.Success -> existing.data ?: return Result.Success(Unit)

                // nothing to archive
                is Result.Error -> return existing.asEmptyDataResult()
            }
        return projectRepository.upsertProject(project.copy(isArchived = isArchived))
    }

    // SOFT-DELETE/RESTORE: stamp (or clear) trashedAt and route through the offline-first upsert so
    // the change is pushed to the server immediately when online (and queued when offline), exactly
    // like archive. A non-null trashedAt trashes the project; null restores it.
    override suspend fun setProjectTrashed(projectId: String, trashedAt: Instant?): EmptyResult<DataError> {
        val project =
            when (val existing = localProjectDataSource.getProjectById(projectId)) {
                is Result.Success -> existing.data ?: return Result.Success(Unit)

                // nothing to trash
                is Result.Error -> return existing.asEmptyDataResult()
            }
        return projectRepository.upsertProject(project.copy(trashedAt = trashedAt))
    }

    // PURGE: permanently delete every project whose trashedAt is older than the cutoff, locally and
    // on the server. Reuses deleteProject so each removal gets the server DELETE + offline fallback.
    override suspend fun purgeExpiredTrashedProjects(cutoff: Instant): EmptyResult<DataError> =
        coroutineScope {
            val expiredIds =
                when (val result = localProjectOrganizationDataSource.getExpiredTrashedProjectIds(cutoff)) {
                    is Result.Success -> result.data
                    is Result.Error -> return@coroutineScope result.asEmptyDataResult()
                }
            expiredIds.map { async { projectRepository.deleteProject(it) } }.awaitAll()
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
            when (val flipped = flipPinned(projectId, isPinned)) {
                is Result.Success -> if (flipped.data) moved += projectId
                is Result.Error -> firstError = firstError ?: flipped
            }
        }
        if (moved.isEmpty()) return firstError ?: Result.Success(Unit)
        return firstError ?: moveToFrontOfPinnedSection(moved)
    }

    // REORDER: persist the manual order shown under the Custom sort filter. orderedProjectIds is the
    // new order of a single section (Pinned or Other); each project's sortIndex is set to its position
    // in that list. A drag is one action for the user, so it is one action here too: one read of the
    // current indices, one transactional local write, one network call. Writing card by card would
    // let a failure halfway through leave two projects sharing an index, which no retry can repair.
    override suspend fun reorderProjects(orderedProjectIds: List<String>): EmptyResult<DataError> {
        val current =
            when (val existing = localProjectOrganizationDataSource.getSortIndices()) {
                is Result.Success -> existing.data
                is Result.Error -> return existing.asEmptyDataResult()
            }
        // Only ids that still exist locally and whose index actually moves.
        val changed =
            buildMap {
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
        val localResult = localProjectOrganizationDataSource.updateSortIndices(changed, updatedAt)
        if (localResult !is Result.Success) {
            return localResult.asEmptyDataResult()
        }

        return when (val remoteResult = remoteProjectDataSource.reorderProjects(changed, updatedAt)) {
            is Result.Success -> {
                Result.Success(Unit)
            }

            is Result.Error -> {
                when {
                    remoteResult.error.isTransient() -> {
                        // Local write already succeeded; only surface an error if queuing the sync fails.
                        val queued = enqueueReorderOperation()
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

    /** Flips one project's pin flag. `Success(false)` means the project is gone: nothing to pin. */
    private suspend fun flipPinned(projectId: String, isPinned: Boolean): Result<Boolean, DataError> {
        val project =
            when (val existing = localProjectDataSource.getProjectById(projectId)) {
                is Result.Success -> existing.data ?: return Result.Success(false)
                is Result.Error -> return existing
            }
        return projectRepository.upsertProject(project.copy(isPinned = isPinned)).map { true }
    }

    // The moved projects go first, keeping the relative order they already had; everyone else in
    // the target section keeps its order behind them. reorderProjects then numbers the whole
    // section from 0 in one transaction and one request.
    private suspend fun moveToFrontOfPinnedSection(moved: List<String>): EmptyResult<DataError> {
        val section =
            when (val allPinnedProjects = localProjectOrganizationDataSource.getPinnedProjects()) {
                is Result.Success -> allPinnedProjects.data
                is Result.Error -> return allPinnedProjects.asEmptyDataResult()
            }
        val movedIds = moved.toSet()
        val (front, rest) =
            section
                .sortedByCustomOrder()
                .map { it.projectId }
                .partition { it in movedIds }

        return reorderProjects(front + rest)
    }

    // One row for the whole order, not one per moved project. The queue's OP_UPDATE dedup rule then
    // collapses further offline reorders into this same row.
    private suspend fun enqueueReorderOperation(): EmptyResult<DataError> =
        pendingSyncDataSource.enqueue(
            entityId = PendingSyncOperation.PROJECT_ORDER_ENTITY_ID,
            entityType = PendingSyncOperation.ENTITY_PROJECT_ORDER,
            operationType = PendingSyncOperation.OP_UPDATE,
            parentEntityId = null,
            createdAt = timeProvider.nowInstant,
        )

    private suspend fun scheduleSync() {
        applicationScope.launch { syncScheduler.schedulePeriodicSync() }.join()
    }
}
