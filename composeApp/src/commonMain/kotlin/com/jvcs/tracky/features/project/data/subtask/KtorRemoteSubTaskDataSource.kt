package com.jvcs.tracky.features.project.data.subtask

import com.jvcs.tracky.core.data.networking.CreateSubTaskRequest
import com.jvcs.tracky.core.data.networking.UpdateSubTaskRequest
import com.jvcs.tracky.core.data.networking.delete
import com.jvcs.tracky.core.data.networking.dto.ProjectSubTaskDto
import com.jvcs.tracky.core.data.networking.get
import com.jvcs.tracky.core.data.networking.mappers.toProjectSubTask
import com.jvcs.tracky.core.data.networking.post
import com.jvcs.tracky.core.data.networking.put
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.data.mappers.toCreateSubTaskRequest
import com.jvcs.tracky.features.project.data.mappers.toUpdateSubTaskRequest
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.subtask.RemoteSubTaskDataSource
import io.ktor.client.HttpClient

/**
 * The project id never appears in a subtask payload — the server derives it from the parent task —
 * so it is handed back down to the mapper from the route.
 */
class KtorRemoteSubTaskDataSource(
    private val httpClient: HttpClient
) : RemoteSubTaskDataSource {

    override suspend fun getSubTasksByTaskId(
        projectId: String,
        taskId: String
    ): Result<List<ProjectSubTask>, DataError.Remote> {
        return httpClient.get<List<ProjectSubTaskDto>>(
            route = "/api/projects/$projectId/tasks/$taskId/subtasks"
        ).map { dtos -> dtos.map { it.toProjectSubTask(projectId) } }
    }

    override suspend fun postSubTask(
        projectId: String,
        taskId: String,
        subTask: ProjectSubTask
    ): Result<ProjectSubTask, DataError.Remote> {
        return httpClient.post<CreateSubTaskRequest, ProjectSubTaskDto>(
            route = "/api/projects/$projectId/tasks/$taskId/subtasks",
            body = subTask.toCreateSubTaskRequest()
        ).map { it.toProjectSubTask(projectId) }
    }

    override suspend fun updateSubTask(
        projectId: String,
        taskId: String,
        subTask: ProjectSubTask
    ): Result<ProjectSubTask, DataError.Remote> {
        return httpClient.put<UpdateSubTaskRequest, ProjectSubTaskDto>(
            route = "/api/projects/$projectId/tasks/$taskId/subtasks/${subTask.projectSubTaskId}",
            body = subTask.toUpdateSubTaskRequest()
        ).map { it.toProjectSubTask(projectId) }
    }

    override suspend fun deleteSubTask(
        projectId: String,
        taskId: String,
        subTaskId: String
    ): EmptyResult<DataError.Remote> {
        return httpClient.delete(
            route = "/api/projects/$projectId/tasks/$taskId/subtasks/$subTaskId"
        )
    }
}
