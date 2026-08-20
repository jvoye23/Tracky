package com.jvcs.tracky.features.project.domain.task

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.coroutines.flow.Flow

interface LocalTaskDataSource {
    /** The stream the task detail screen observes. */
    fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?>

    /** One-shot read of the same row — repositories need a value, not a subscription. */
    suspend fun getTaskById(taskId: String): Result<ProjectTask?, DataError.Local>

    suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError.Local>
    suspend fun deleteProjectTask(taskId: String): EmptyResult<DataError.Local>
    suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long): EmptyResult<DataError.Local>
    suspend fun updateTaskTitle(taskId: String, title: String): EmptyResult<DataError.Local>

    /**
     * Opens a new interval and flags the task's timer as running.
     *
     * Returns the interval it created so the caller can push it remotely — the id is generated in
     * here, so there is no other way for the repository to know which row to sync.
     */
    suspend fun startTask(taskId: String): Result<TaskInterval, DataError.Local>

    /**
     * Closes the task's open interval, adds its duration to the task and clears the timer flag.
     *
     * Returns the interval it just closed, or null when the timer was not running — again so the
     * caller can push exactly that row.
     */
    suspend fun stopTask(taskId: String): Result<TaskInterval?, DataError.Local>
}
