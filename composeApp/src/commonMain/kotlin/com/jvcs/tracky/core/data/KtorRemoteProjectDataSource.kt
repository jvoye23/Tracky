package com.jvcs.tracky.core.data

import com.jvcs.tracky.core.data.networking.CreateProjectRequest
import com.jvcs.tracky.core.data.networking.CreateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.CreateTaskIntervalRequest
import com.jvcs.tracky.core.data.networking.ProjectSortOrderDto
import com.jvcs.tracky.core.data.networking.ReorderProjectsRequest
import com.jvcs.tracky.core.data.networking.UpdateProjectRequest
import com.jvcs.tracky.core.data.networking.UpdateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.UpdateTaskIntervalRequest
import com.jvcs.tracky.core.data.networking.delete
import com.jvcs.tracky.core.data.networking.dto.ProjectDto
import com.jvcs.tracky.core.data.networking.dto.ProjectTaskDto
import com.jvcs.tracky.core.data.networking.dto.TaskIntervalDto
import com.jvcs.tracky.core.data.networking.get
import com.jvcs.tracky.core.data.networking.mappers.toProject
import com.jvcs.tracky.core.data.networking.mappers.toProjectTask
import com.jvcs.tracky.core.data.networking.mappers.toTaskInterval
import com.jvcs.tracky.core.data.networking.post
import com.jvcs.tracky.core.data.networking.put
import com.jvcs.tracky.core.domain.RemoteProjectDataSource
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.core.mapper.toCreateProjectRequest
import com.jvcs.tracky.core.mapper.toCreateProjectTaskRequest
import com.jvcs.tracky.core.mapper.toCreateTaskIntervalRequest
import com.jvcs.tracky.core.mapper.toUpdateProjectRequest
import com.jvcs.tracky.core.mapper.toUpdateProjectTaskRequest
import com.jvcs.tracky.core.mapper.toUpdateTaskIntervalRequest
import io.ktor.client.HttpClient
import kotlin.time.Instant

class KtorRemoteProjectDataSource(
    private val httpClient: HttpClient
): RemoteProjectDataSource {
    override suspend fun getProjects(): Result<List<Project>, DataError.Remote> {
        return httpClient.get<List<ProjectDto>>(
            route = "/api/projects"
        ).map {
            it.map { dto -> dto.toProject() }
        }
    }

    override suspend fun postProject(project: Project): Result<Project, DataError.Remote> {
        return httpClient.post<CreateProjectRequest, ProjectDto>(
            route = "/api/projects",
            body = project.toCreateProjectRequest()
        ).map { it.toProject() }
    }

    override suspend fun updateProject(project: Project): Result<Project, DataError.Remote> {
        return httpClient.put<UpdateProjectRequest, ProjectDto>(
            route = "api/projects/${project.projectId}",
            body = project.toUpdateProjectRequest()
        ).map { it.toProject()
        }
    }

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError.Remote> {
        return httpClient.delete(
            route = "api/projects/$projectId"
        )
    }

    // One request for the whole sort gesture. The endpoint answers 204, so nothing comes back that could
    // overwrite the order we just wrote locally.
    override suspend fun reorderProjects(indices: Map<String, Long>, updatedAt: Instant): EmptyResult<DataError.Remote> {
        return httpClient.put<ReorderProjectsRequest, Unit>(
            route = "/api/projects/sort",
            body = ReorderProjectsRequest(
                updatedAtUtc = updatedAt.toString(),
                items = indices.map { (projectId, sortIndex) ->
                    ProjectSortOrderDto(projectId = projectId, sortIndex = sortIndex)
                }
            )
        )
    }

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

    override suspend fun postInterval(
        projectId: String,
        taskId: String,
        interval: TaskInterval
    ): Result<TaskInterval, DataError.Remote> {
        return httpClient.post<CreateTaskIntervalRequest, TaskIntervalDto>(
            route = "/api/projects/$projectId/tasks/$taskId/intervals",
            body = interval.toCreateTaskIntervalRequest()
        ).map { it.toTaskInterval() }
    }

    override suspend fun updateInterval(
        projectId: String,
        taskId: String,
        interval: TaskInterval
    ): Result<TaskInterval, DataError.Remote> {
        return httpClient.put<UpdateTaskIntervalRequest, TaskIntervalDto>(
            route = "/api/projects/$projectId/tasks/$taskId/intervals/${interval.intervalId}",
            body = interval.toUpdateTaskIntervalRequest()
        ).map { it.toTaskInterval() }
    }

    override suspend fun deleteInterval(
        projectId: String,
        taskId: String,
        intervalId: String
    ): EmptyResult<DataError.Remote> {
        return httpClient.delete(
            route = "/api/projects/$projectId/tasks/$taskId/intervals/$intervalId"
        )
    }
}