package com.jvcs.tracky.features.project.domain.subtask

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import kotlinx.coroutines.flow.Flow

interface LocalSubTaskDataSource {
    /** The stream the task detail screen observes, each subtask carrying its intervals. */
    fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>>

    /** One-shot read — repositories need a value, not a subscription. */
    suspend fun getSubTaskById(subTaskId: String): Result<ProjectSubTask?, DataError.Local>

    suspend fun upsertSubTask(subTask: ProjectSubTask): EmptyResult<DataError.Local>
    suspend fun deleteSubTask(subTaskId: String): EmptyResult<DataError.Local>

    /**
     * Opens an interval on [subTaskId] and flags its timer as running.
     *
     * A subtask interval cannot exist outside a task interval, so this also makes sure the parent
     * task has one open: if the task timer is already running the new interval nests inside the
     * interval already there, otherwise the task is started too and the new row remembers that it
     * was the one to do it.
     *
     * Only one subtask under a task may run at a time — a sibling still running is closed first.
     */
    suspend fun startSubTask(subTaskId: String): Result<SubTaskTimerChange, DataError.Local>

    /**
     * Closes the subtask's open interval, banks its duration and clears its timer flag.
     *
     * The parent task keeps running — unless this subtask is what started it, which the interval
     * itself records, in which case the task is stopped again too.
     *
     * Returns null when the timer was not running.
     */
    suspend fun stopSubTask(subTaskId: String): Result<SubTaskTimerChange?, DataError.Local>

    /** Id of the subtask under [taskId] whose timer ran most recently, or null if none ever has. */
    suspend fun lastStartedSubTaskId(taskId: String): Result<String?, DataError.Local>
}
