package com.jvcs.tracky.core.domain

import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import kotlin.time.Instant

interface RemoteProjectDataSource {
    suspend fun getProjects(): Result<List<Project>, DataError.Remote>
    suspend fun postProject(project: Project): Result<Project, DataError.Remote>
    suspend fun updateProject(project: Project): Result<Project, DataError.Remote>
    suspend fun deleteProject(projectId: String): EmptyResult<DataError.Remote>
    /** Pushes a whole reorder as one call: only the moved projects, one shared updatedAt. */
    suspend fun reorderProjects(indices: Map<String, Long>, updatedAt: Instant): EmptyResult<DataError.Remote>
    suspend fun getTasksByProjectId(projectId: String): Result<List<ProjectTask>, DataError.Remote>
    suspend fun postTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Remote>
    suspend fun updateTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Remote>
    suspend fun deleteTask(projectId: String, taskId: String): EmptyResult<DataError.Remote>
}


