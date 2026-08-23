package com.jvcs.tracky.features.project.domain.subtask

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import kotlinx.coroutines.flow.Flow

/**
 * Subtasks sync through their own routes nested under the task, so this repository queues and
 * drains like the project, task and interval ones do.
 *
 * The timer calls reach further than the others: starting a subtask can open a task interval and
 * stopping it can close one, and those rows belong to the interval and task repositories, so the
 * two timer calls push through those rather than duplicating their rules here.
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
