package com.jvcs.tracky.features.project.data.project

import com.jvcs.tracky.core.data.networking.CreateProjectRequest
import com.jvcs.tracky.core.data.networking.ProjectSortOrderDto
import com.jvcs.tracky.core.data.networking.ReorderProjectsRequest
import com.jvcs.tracky.core.data.networking.UpdateProjectRequest
import com.jvcs.tracky.core.data.networking.delete
import com.jvcs.tracky.core.data.networking.dto.ProjectDto
import com.jvcs.tracky.core.data.networking.get
import com.jvcs.tracky.core.data.networking.mappers.toProject
import com.jvcs.tracky.core.data.networking.post
import com.jvcs.tracky.core.data.networking.put
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.data.mappers.toCreateProjectRequest
import com.jvcs.tracky.features.project.data.mappers.toUpdateProjectRequest
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.project.RemoteProjectDataSource
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
}
