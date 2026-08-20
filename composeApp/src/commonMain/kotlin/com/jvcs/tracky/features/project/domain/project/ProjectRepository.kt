package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface ProjectRepository {

    fun getProjects(): Flow<List<Project>>
    fun getActiveProjects(): Flow<List<Project>>
    suspend fun fetchProjects(): Result<List<Project>, DataError.Remote>
    fun getArchivedProjects(): Flow<List<Project>>
    fun getTrashedProjects(): Flow<List<Project>>
    suspend fun getProjectById(projectId: String): Project?
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
    suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError>
    suspend fun deleteProject(projectId: String): EmptyResult<DataError>
    suspend fun deleteProjectTask(taskId: String)
    suspend fun deleteAllProjects()

    suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long)

    fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?>

    suspend fun startTask(taskId: String)
    suspend fun stopTask(taskId: String)
    suspend fun updateTaskTitle(taskId: String, title: String)
}