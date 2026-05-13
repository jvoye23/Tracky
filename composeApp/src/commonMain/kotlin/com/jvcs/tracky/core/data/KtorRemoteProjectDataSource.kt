package com.jvcs.tracky.core.data

import com.jvcs.tracky.core.data.networking.CreateProjectRequest
import com.jvcs.tracky.core.data.networking.CreateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.delete
import com.jvcs.tracky.core.data.networking.dto.ProjectDto
import com.jvcs.tracky.core.data.networking.get
import com.jvcs.tracky.core.data.networking.mappers.toProject
import com.jvcs.tracky.core.data.networking.post
import com.jvcs.tracky.core.data.networking.put
import com.jvcs.tracky.core.database.relation.ProjectWithTasks
import com.jvcs.tracky.core.domain.RemoteProjectDataSource
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.core.mapper.toCreateProjectRequest
import com.jvcs.tracky.core.mapper.toCreateProjectTaskRequest
import io.ktor.client.HttpClient

class KtorRemoteProjectDataSource(
    private val httpClient: HttpClient
): RemoteProjectDataSource {
    override suspend fun getProjects(): Result<List<Project>, DataError.Network> {
        return httpClient.get<Project>(
            route = "/api/projects"
        ).map {
            listOf(it)
        }
    }

    override suspend fun postProject(project: Project): Result<Project, DataError.Network> {
        return httpClient.post<CreateProjectRequest, ProjectDto>(
            route = "/api/projects",
            body = project.toCreateProjectRequest()
        ).map { it.toProject() }
    }

    override suspend fun updateProject(project: Project): Result<Project, DataError.Network> {
        return httpClient.put<CreateProjectRequest, Project>(
            route = "api/projects/${project.projectId}",
            body = project.toCreateProjectRequest()
        )
    }

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError.Network> {
        return httpClient.delete(
            route = "api/projects/$projectId"
        )
    }

    override suspend fun getTasksByProjectId(projectId: String): Result<ProjectWithTasks, DataError.Network> {
        return httpClient.get<ProjectWithTasks>(
            route = "/api/projects/${projectId}/tasks"
        )
    }

    override suspend fun postTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Network> {
        return httpClient.post<CreateProjectTaskRequest, ProjectTask>(
            route = "/api/projects/${projectId}/tasks",
            body = task.toCreateProjectTaskRequest()
        )
    }
}