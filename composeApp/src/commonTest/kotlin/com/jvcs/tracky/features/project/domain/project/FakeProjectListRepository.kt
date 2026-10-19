package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

/** Serves [projects] as every stored project. Writes do nothing. */
class FakeProjectListRepository : ProjectRepository {

    val projects = MutableStateFlow(emptyList<Project>())

    override fun getProjects(): Flow<List<Project>> = projects

    // Unused by the classes this serves; stubbed rather than implemented.
    override fun getActiveProjects(): Flow<List<Project>> = projects

    override suspend fun fetchProjects(): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun getProjectById(projectId: String): Result<Project?, DataError> = Result.Success(null)

    override fun observeProjectById(projectId: String): Flow<Project?> = flowOf(null)

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Result<Project?, DataError> =
        Result.Success(null)

    override fun observeProjectWithTaskTreeById(projectId: String): Flow<Project?> = flowOf(null)

    override suspend fun upsertProject(project: Project): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun deleteAllProjects(): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun syncPendingProjects(): EmptyResult<DataError> = Result.Success(Unit)
}

/** A stored project with nothing but [id] set. */
fun storedProject(id: String) =
    Project(
        projectId = id,
        title = id,
        description = null,
        colorArgb = null,
        totalDurationMillis = null,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        isFinished = false,
        endDateTimeUtc = null,
    )
