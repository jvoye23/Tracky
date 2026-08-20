package com.jvcs.tracky.features.project.data.task

import com.jvcs.tracky.core.data.networking.CreateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.UpdateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.delete
import com.jvcs.tracky.core.data.networking.dto.ProjectTaskDto
import com.jvcs.tracky.core.data.networking.get
import com.jvcs.tracky.core.data.networking.mappers.toProjectTask
import com.jvcs.tracky.core.data.networking.post
import com.jvcs.tracky.core.data.networking.put
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.data.mappers.toCreateProjectTaskRequest
import com.jvcs.tracky.features.project.data.mappers.toUpdateProjectTaskRequest
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.task.RemoteTaskDataSource
import io.ktor.client.HttpClient

class KtorRemoteTaskDataSource(
    private val httpClient: HttpClient
) : RemoteTaskDataSource {

    override suspend fun getTasksByProjectId(projectId: String): Result<List<ProjectTask>, DataError.Remote> {
        return httpClient.get<List<ProjectTaskDto>>(
            route = "/api/projects/${projectId}/tasks"
        ).map {
            it.map { projectTaskDto -> projectTaskDto.toProjectTask(projectId) }
        }
    }

    override suspend fun postTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Remote> {
        return httpClient.post<CreateProjectTaskRequest, ProjectTaskDto>(
            route = "/api/projects/${projectId}/tasks",
            body = task.toCreateProjectTaskRequest()
        ).map { it.toProjectTask(projectId) }
    }

    override suspend fun updateTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Remote> {
        return httpClient.put<UpdateProjectTaskRequest, ProjectTaskDto>(
            route = "/api/projects/${projectId}/tasks/${task.projectTaskId}",
            body = task.toUpdateProjectTaskRequest()
        ).map { it.toProjectTask(projectId) }
    }

    override suspend fun deleteTask(projectId: String, taskId: String): EmptyResult<DataError.Remote> {
        return httpClient.delete(
            route = "/api/projects/$projectId/tasks/$taskId"
        )
    }
}
