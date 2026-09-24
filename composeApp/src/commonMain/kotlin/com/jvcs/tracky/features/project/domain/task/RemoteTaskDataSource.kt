package com.jvcs.tracky.features.project.domain.task

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import kotlin.time.Instant

interface RemoteTaskDataSource {
    suspend fun getTasksByProjectId(projectId: String): Result<List<ProjectTask>, DataError.Remote>

    suspend fun postTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Remote>

    suspend fun updateTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Remote>

    suspend fun deleteTask(projectId: String, taskId: String): EmptyResult<DataError.Remote>

    /** One request for a whole reorder gesture — see RemoteProjectDataSource.reorderProjects. */
    suspend fun reorderTasks(
        projectId: String,
        indices: Map<String, Long>,
        updatedAt: Instant,
    ): EmptyResult<DataError.Remote>
}
