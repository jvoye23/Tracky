package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.domain.RemoteProjectDataSource
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.asEmptyDataResult
import com.jvcs.tracky.features.project_tracker.domain.LocalProjectDataSource
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow

class OfflineFirstProjectRepository(
    private val localProjectDataSource: LocalProjectDataSource,
    private val remoteProjectDataSource: RemoteProjectDataSource,
    private val applicationScope: CoroutineScope
): ProjectRepository {
    override suspend fun fetchProjects(): EmptyResult<DataError> {
        return when(val result = remoteProjectDataSource.getProjects()) {
            is Result.Error -> result.asEmptyDataResult()
            is Result.Success -> {
                applicationScope.async {
                    localProjectDataSource.upsertProjects(result.data).asEmptyDataResult()
                }.await()
            }
        }
    }

    override fun getProjects(): Flow<List<Project>> {
        return localProjectDataSource.getProjects()
    }

    override suspend fun getProjectById(projectId: String): Project? {
        return localProjectDataSource.getProjectById(projectId)
    }

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? {
        return localProjectDataSource.getProjectWithTasksByProjectId(projectId)
    }

    override suspend fun upsertProject(project: Project): EmptyResult<DataError> {
        val localResult = localProjectDataSource.upsertProject(project)
        if (localResult !is Result.Success) {
            return localResult.asEmptyDataResult()
        }
        return when (val remoteResult = remoteProjectDataSource.postProject(project)) {
            is Result.Error -> remoteResult.asEmptyDataResult()
            is Result.Success -> {
                applicationScope.async {
                    // TODO: handle is synced later
                }.await()
                Result.Success(Unit)
            }
        }
    }

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> {
        val localResult = localProjectDataSource.upsertProjectTask(projectTask)
        if (localResult !is Result.Success) {
            return localResult.asEmptyDataResult()
        }
        return when (val remoteResult = remoteProjectDataSource.postTaskByProjectId(
            projectId = projectTask.parentProjectId,
            task = projectTask
        )) {
            is Result.Error -> remoteResult.asEmptyDataResult()
            is Result.Success -> {
                applicationScope.async {
                    // TODO: handle is synced later
                }.await()
                Result.Success(Unit)
            }
        }
    }

    override suspend fun deleteProject(projectId: String) {
        localProjectDataSource.deleteProject(projectId)
    }

    override suspend fun deleteProjectTask(taskId: String) {
        localProjectDataSource.deleteProjectTask(taskId)
    }

    override suspend fun deleteAllProjects() {
        localProjectDataSource.deleteAllProjects()
    }

    override suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long) {
        localProjectDataSource.updateTaskDuration(taskId, newDurationMillis)
    }

    override fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> {
        return localProjectDataSource.getTaskWithIntervalsById(taskId)
    }

    override suspend fun upsertTaskInterval(interval: TaskInterval) {
        localProjectDataSource.upsertTaskInterval(interval)
    }

    override suspend fun getOpenIntervalByTaskId(taskId: String): TaskInterval? {
        return localProjectDataSource.getOpenIntervalByTaskId(taskId)
    }

    override suspend fun startTask(taskId: String) {
        localProjectDataSource.startTask(taskId)
    }

    override suspend fun stopTask(taskId: String) {
        localProjectDataSource.stopTask(taskId)
    }

    override suspend fun updateTaskTitle(taskId: String, title: String) {
        localProjectDataSource.updateTaskTitle(taskId, title)
    }

}