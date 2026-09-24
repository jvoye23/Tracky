@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project.presentation.fakes

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.domain.models.Project
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

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun deleteAllProjects(): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun syncPendingProjects(): EmptyResult<DataError> = Result.Success(Unit)
}
