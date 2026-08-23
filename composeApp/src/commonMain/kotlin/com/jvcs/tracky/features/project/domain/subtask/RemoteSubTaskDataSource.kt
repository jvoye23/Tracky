package com.jvcs.tracky.features.project.domain.subtask

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask

/**
 * Subtasks are written through their own endpoints nested under the task — the task create and
 * update bodies do not carry them (see Requirements/api/backend_documentation.md).
 *
 * Ordinary reads come back nested inside GET /api/projects, so [getSubTasksByTaskId] exists only
 * for conflict resolution: a 409 means the server already has the row, and last-write-wins needs
 * the server's copy to compare against. Same reason RemoteTaskDataSource keeps its list method.
 */
interface RemoteSubTaskDataSource {
    suspend fun getSubTasksByTaskId(
        projectId: String,
        taskId: String
    ): Result<List<ProjectSubTask>, DataError.Remote>

    suspend fun postSubTask(
        projectId: String,
        taskId: String,
        subTask: ProjectSubTask
    ): Result<ProjectSubTask, DataError.Remote>

    suspend fun updateSubTask(
        projectId: String,
        taskId: String,
        subTask: ProjectSubTask
    ): Result<ProjectSubTask, DataError.Remote>

    /** Takes the ids explicitly: by the time a queued delete drains, the local row is already gone. */
    suspend fun deleteSubTask(
        projectId: String,
        taskId: String,
        subTaskId: String
    ): EmptyResult<DataError.Remote>
}
