package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface ProjectRepository {

    fun getProjects(): Flow<List<Project>>

    fun getActiveProjects(): Flow<List<Project>>

    suspend fun fetchProjects(): EmptyResult<DataError>

    suspend fun getProjectById(projectId: String): Project?

    /**
     * The project row as a live stream, without its task tree. Lets a screen stay current with
     * edits made elsewhere — another screen's ViewModel, or a sync pull — instead of holding the
     * snapshot it read on entry.
     */
    fun observeProjectById(projectId: String): Flow<Project?>

    suspend fun getProjectWithTasksByProjectId(projectId: String): Project?

    fun observeProjectWithTaskTreeById(projectId: String): Flow<Project?>

    suspend fun upsertProject(project: Project): EmptyResult<DataError>

    suspend fun deleteProject(projectId: String): EmptyResult<DataError>

    suspend fun deleteAllProjects()

    /**
     * Drains the queued project writes (including the manual sort order).
     *
     * Runs first of the three drains: every task and interval hangs off a project route, so nothing
     * else can be pushed until the projects exist server-side.
     */
    suspend fun syncPendingProjects(): EmptyResult<DataError>
}
