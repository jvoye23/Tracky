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
    fun getArchivedProjects(): Flow<List<Project>>
    fun getTrashedProjects(): Flow<List<Project>>
    suspend fun getProjectById(projectId: String): Project?
    /**
     * The project row as a live stream, without its task tree. Lets a screen stay current with
     * edits made elsewhere — another screen's ViewModel, or a sync pull — instead of holding the
     * snapshot it read on entry.
     */
    fun observeProjectById(projectId: String): Flow<Project?>
    suspend fun getProjectWithTasksByProjectId(projectId: String): Project?
    suspend fun upsertProject(project: Project): EmptyResult<DataError>
    suspend fun setProjectArchived(projectId: String, isArchived: Boolean): EmptyResult<DataError>
    suspend fun setProjectTrashed(projectId: String, trashedAt: Instant?): EmptyResult<DataError>
    suspend fun purgeExpiredTrashedProjects(cutoff: Instant): EmptyResult<DataError>
    /**
     * Pins or unpins every id in one gesture. The affected projects move to the top of their new
     * section and the rest of that section is re-indexed behind them.
     */
    suspend fun setProjectsPinned(projectIds: List<String>, isPinned: Boolean): EmptyResult<DataError>
    suspend fun reorderProjects(orderedProjectIds: List<String>): EmptyResult<DataError>
    suspend fun deleteProject(projectId: String): EmptyResult<DataError>
    suspend fun deleteAllProjects()

    /**
     * Drains the queued project writes (including the manual sort order).
     *
     * Runs first of the three drains: every task and interval hangs off a project route, so nothing
     * else can be pushed until the projects exist server-side.
     */
    suspend fun syncPendingProjects()
}
