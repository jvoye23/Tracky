@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project.presentation.fakes

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.project.ProjectOrganizationRepository
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class FixedTimeProvider(override val nowInstant: Instant) : TimeProvider {
    override val nowZoneTimeInUtc: LocalDateTime get() = nowInstant.toLocalDateTime(TimeZone.UTC)
}

/** Counts tree reads, which is what the in-memory-derivation tests assert on. */
class FakeProjectRepository(private val project: Project?) : ProjectRepository {
    var treeReads = 0
        private set

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Result<Project?, DataError> {
        treeReads++
        return Result.Success(project)
    }

    // Unused by this screen; stubbed rather than implemented.
    override fun getProjects(): Flow<List<Project>> = flowOf(emptyList())

    override fun getActiveProjects(): Flow<List<Project>> = flowOf(emptyList())

    override suspend fun fetchProjects(): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun getProjectById(projectId: String): Result<Project?, DataError> = Result.Success(project)

    override fun observeProjectById(projectId: String): Flow<Project?> = flowOf(project)

    override fun observeProjectWithTaskTreeById(projectId: String): Flow<Project?> = flowOf(project)

    override suspend fun upsertProject(project: Project): EmptyResult<DataError> = Result.Success(Unit)

    var deleteResult: EmptyResult<DataError> = Result.Success(Unit)
    val deletedIds = mutableListOf<String>()

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError> {
        deletedIds += projectId
        return deleteResult
    }

    override suspend fun deleteAllProjects(): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun syncPendingProjects(): EmptyResult<DataError> = Result.Success(Unit)
}

/** Serves fixed archived and trashed lists and records the organisation writes. */
class FakeProjectOrganizationRepository(
    private val archived: List<Project> = emptyList(),
    private val trashed: List<Project> = emptyList(),
) : ProjectOrganizationRepository {
    var writeResult: EmptyResult<DataError> = Result.Success(Unit)
    val archivedCalls = mutableListOf<Pair<String, Boolean>>()
    val trashedCalls = mutableListOf<Pair<String, Instant?>>()

    override fun getArchivedProjects(): Flow<List<Project>> = flowOf(archived)

    override fun getTrashedProjects(): Flow<List<Project>> = flowOf(trashed)

    override suspend fun setProjectArchived(projectId: String, isArchived: Boolean): EmptyResult<DataError> {
        archivedCalls += projectId to isArchived
        return writeResult
    }

    override suspend fun setProjectTrashed(projectId: String, trashedAt: Instant?): EmptyResult<DataError> {
        trashedCalls += projectId to trashedAt
        return writeResult
    }

    override suspend fun purgeExpiredTrashedProjects(cutoff: Instant): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun setProjectsPinned(projectIds: List<String>, isPinned: Boolean): EmptyResult<DataError> =
        Result.Success(Unit)

    override suspend fun reorderProjects(orderedProjectIds: List<String>): EmptyResult<DataError> = Result.Success(Unit)
}
