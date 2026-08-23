package com.jvcs.tracky.features.project.domain.subtask

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import kotlinx.coroutines.flow.Flow

/**
 * Subtasks themselves are local-only — the backend has no routes for them — so unlike the project,
 * task and interval repositories this one queues nothing and drains nothing.
 *
 * The timer is the exception. Starting a subtask can open a task interval and stopping it can close
 * one, and those *do* sync, so the two timer calls push through the interval and task repositories
 * while the rest stay purely local.
 */
interface SubTaskRepository {

    fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>>

    suspend fun upsertSubTask(subTask: ProjectSubTask): EmptyResult<DataError>
    suspend fun deleteSubTask(subTaskId: String): EmptyResult<DataError>

    /** Starts the subtask's timer, starting its parent task's too when that is not running. */
    suspend fun startSubTask(subTaskId: String): EmptyResult<DataError>

    /** Stops the subtask's timer, stopping its parent task's when this subtask started it. */
    suspend fun stopSubTask(subTaskId: String): EmptyResult<DataError>
}
