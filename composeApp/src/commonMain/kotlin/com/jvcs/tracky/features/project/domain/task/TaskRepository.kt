package com.jvcs.tracky.features.project.domain.task

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import kotlinx.coroutines.flow.Flow

interface ProjectTaskRepository {

    suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError>
    suspend fun deleteProjectTask(projectId: String, taskId: String): EmptyResult<DataError>
    suspend fun updateProjectTaskDuration(taskId: String, newDurationMillis: Long): EmptyResult<DataError>
    suspend fun updateProjectTaskTitle(taskId: String, title: String): EmptyResult<DataError>
    fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?>
    suspend fun startProjectTask(taskId: String): EmptyResult<DataError>
    suspend fun stopProjectTask(taskId: String): EmptyResult<DataError>

    /**
     * Drains the queued task writes. Runs after the project drain: a task has no route until its
     * project exists on the server, so ops whose project is still pending stay queued.
     */
    suspend fun syncPendingTasks()
}
