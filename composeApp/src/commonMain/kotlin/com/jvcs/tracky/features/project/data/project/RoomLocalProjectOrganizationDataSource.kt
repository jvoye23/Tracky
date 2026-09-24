package com.jvcs.tracky.features.project.data.project

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.dao.ProjectTreeDao
import com.jvcs.tracky.core.database.dao.SortOrderDao
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.data.mappers.toProject
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.project.LocalProjectOrganizationDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

class RoomLocalProjectOrganizationDataSource(
    private val projectDao: ProjectDao,
    private val projectTreeDao: ProjectTreeDao,
    private val sortOrderDao: SortOrderDao,
) : LocalProjectOrganizationDataSource {

    override fun getArchivedProjects(): Flow<List<Project>> =
        projectTreeDao
            .getArchivedProjectsWithTasks()
            .map { list -> list.map { it.toProject() } }

    override fun getTrashedProjects(): Flow<List<Project>> =
        projectTreeDao
            .getTrashedProjectsWithTasks()
            .map { list -> list.map { it.toProject() } }

    override suspend fun getPinnedProjects(): Result<List<Project>, DataError.Local> =
        read {
            projectTreeDao.getPinnedProjectsWithTasks().first().map { it.toProject() }
        }

    override suspend fun getExpiredTrashedProjectIds(cutoff: Instant): Result<List<String>, DataError.Local> =
        read {
            projectDao.getExpiredTrashedProjectIds(cutoff.toEpochMilliseconds())
        }

    override suspend fun getSortIndices(): Result<Map<String, Long?>, DataError.Local> =
        read {
            sortOrderDao.getSortIndices().associate { it.projectId to it.sortIndex }
        }

    override suspend fun updateSortIndices(
        indices: Map<String, Long>,
        updatedAt: Instant,
    ): EmptyResult<DataError.Local> =
        write {
            sortOrderDao.updateSortIndices(indices, updatedAt.toEpochMilliseconds())
        }

    private inline fun <T> read(block: () -> T): Result<T, DataError.Local> = roomRead(TAG, block)

    private suspend fun write(block: suspend () -> Unit): EmptyResult<DataError.Local> = roomWrite(TAG, block)

    private companion object {
        const val TAG = "RoomLocalProjectOrganizationDataSource"
    }
}
