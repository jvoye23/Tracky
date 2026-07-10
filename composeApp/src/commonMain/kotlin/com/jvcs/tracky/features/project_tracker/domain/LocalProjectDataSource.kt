package com.jvcs.tracky.features.project_tracker.domain

import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

typealias ProjectId = String

interface LocalProjectDataSource {
    fun getProjects(): Flow<List<Project>>
    fun getArchivedProjects(): Flow<List<Project>>
    fun getTrashedProjects(): Flow<List<Project>>
    suspend fun getExpiredTrashedProjectIds(cutoff: Instant): List<String>
    suspend fun getProjectById(projectId: String): Project?
    suspend fun getProjectWithTasksByProjectId(projectId: String): Project?
    suspend fun upsertProject(project: Project): EmptyResult<DataError>
    suspend fun upsertProjects(projects: List<Project>): EmptyResult<DataError>
    suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError>
    suspend fun deleteProject(projectId: String)
    suspend fun deleteProjectTask(taskId: String)
    suspend fun deleteAllProjects()
    suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long)
    fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?>
    suspend fun upsertTaskInterval(interval: TaskInterval): EmptyResult<DataError>
    suspend fun getOpenIntervalByTaskId(taskId: String): TaskInterval?
    suspend fun startTask(taskId: String)
    suspend fun stopTask(taskId: String)
    suspend fun updateTaskTitle(taskId: String, title: String)
}

