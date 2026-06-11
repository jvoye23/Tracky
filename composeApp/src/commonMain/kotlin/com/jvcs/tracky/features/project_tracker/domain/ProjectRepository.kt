package com.jvcs.tracky.features.project_tracker.domain

import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {

    suspend fun fetchProjects(): EmptyResult<DataError>

    /** Drains the pending-sync queue, pushing queued creates/updates/deletes to the backend. */
    suspend fun syncPendingOperations()

    fun getProjects(): Flow<List<Project>>
    suspend fun getProjectById(projectId: String): Project?
    suspend fun getProjectWithTasksByProjectId(projectId: String): Project?
    suspend fun upsertProject(project: Project): EmptyResult<DataError>
    suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError>
    suspend fun deleteProject(projectId: String)
    suspend fun deleteProjectTask(taskId: String)
    suspend fun deleteAllProjects()

    suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long)

    fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?>
    suspend fun upsertTaskInterval(interval: TaskInterval)
    suspend fun getOpenIntervalByTaskId(taskId: String): TaskInterval?
    
    suspend fun startTask(taskId: String)
    suspend fun stopTask(taskId: String)
    suspend fun updateTaskTitle(taskId: String, title: String)
}