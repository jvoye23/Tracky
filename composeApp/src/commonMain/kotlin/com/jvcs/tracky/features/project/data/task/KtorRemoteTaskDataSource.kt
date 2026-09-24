package com.jvcs.tracky.features.project.data.task

import com.jvcs.tracky.core.data.networking.CreateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.ReorderTasksRequest
import com.jvcs.tracky.core.data.networking.UpdateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.delete
import com.jvcs.tracky.core.data.networking.dto.ProjectTaskDto
import com.jvcs.tracky.core.data.networking.dto.TaskSortOrderDto
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
import kotlin.time.Instant

class KtorRemoteTaskDataSource(private val httpClient: HttpClient) : RemoteTaskDataSource {

    override suspend fun getTasksByProjectId(projectId: String): Result<List<ProjectTask>, DataError.Remote> =
        httpClient
            .get<List<ProjectTaskDto>>(
                route = "/api/projects/$projectId/tasks",
            ).map {
                it.map { projectTaskDto -> projectTaskDto.toProjectTask(projectId) }
            }

    override suspend fun postTaskByProjectId(
        projectId: String,
        task: ProjectTask,
    ): Result<ProjectTask, DataError.Remote> =
        httpClient
            .post<CreateProjectTaskRequest, ProjectTaskDto>(
                route = "/api/projects/$projectId/tasks",
                body = task.toCreateProjectTaskRequest(),
            ).map { it.toProjectTask(projectId) }

    override suspend fun updateTaskByProjectId(
        projectId: String,
        task: ProjectTask,
    ): Result<ProjectTask, DataError.Remote> =
        httpClient
            .put<UpdateProjectTaskRequest, ProjectTaskDto>(
                route = "/api/projects/$projectId/tasks/${task.projectTaskId}",
                body = task.toUpdateProjectTaskRequest(),
            ).map { it.toProjectTask(projectId) }

    override suspend fun deleteTask(projectId: String, taskId: String): EmptyResult<DataError.Remote> =
        httpClient.delete(
            route = "/api/projects/$projectId/tasks/$taskId",
        )

    // One request for the whole sort gesture. The endpoint answers 204, so nothing comes back that
    // could overwrite the order we just wrote locally.
    override suspend fun reorderTasks(
        projectId: String,
        indices: Map<String, Long>,
        updatedAt: Instant,
    ): EmptyResult<DataError.Remote> =
        httpClient.put<ReorderTasksRequest, Unit>(
            route = "/api/projects/$projectId/tasks/sort",
            body =
                ReorderTasksRequest(
                    updatedAtUtc = updatedAt.toString(),
                    items =
                        indices.map { (taskId, sortIndex) ->
                            TaskSortOrderDto(id = taskId, sortIndex = sortIndex)
                        },
                ),
        )
}
