package com.jvcs.tracky.core.domain

import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.client.statement.HttpResponse

interface RemoteProjectDataSource {
    suspend fun getProjects(): Result<List<Project>, DataError.Network>
    suspend fun postProject(project: Project): Result<Project, DataError.Network>
    suspend fun updateProject(project: Project): Result<Project, DataError.Network>
    suspend fun deleteProject(projectId: String): EmptyResult<DataError.Network>
    suspend fun getTasksByProjectId(projectId: String): Result<List<ProjectTask>, DataError.Network>
    suspend fun postTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Network>
    suspend fun updateTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Network>
    suspend fun deleteTask(projectId: String, taskId: String): EmptyResult<DataError.Network>
}


