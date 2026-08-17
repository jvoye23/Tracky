package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
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

    // Intervals are written through their own endpoints — the task create/update bodies do not
    // carry them (see Requirements/api/api_endpoints.md). Reads come back nested inside
    // GET /api/projects, so there is deliberately no interval read method here.
    suspend fun postInterval(
        projectId: String,
        taskId: String,
        interval: TaskInterval
    ): Result<TaskInterval, DataError.Remote>

    suspend fun updateInterval(
        projectId: String,
        taskId: String,
        interval: TaskInterval
    ): Result<TaskInterval, DataError.Remote>

    suspend fun deleteInterval(
        projectId: String,
        taskId: String,
        intervalId: String
    ): EmptyResult<DataError.Remote>
}