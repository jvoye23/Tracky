package com.jvcs.tracky.features.project.domain.subtask

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval

interface LocalSubTaskDataSource {
    /**
     * Opens an interval on [subTaskId] and flags its timer as running.
     *
     * A subtask interval cannot exist outside a task interval, so this also makes sure the parent
     * task has one open: if the task timer is already running the new interval nests inside the
     * interval already there, otherwise the task is started too and the new row remembers that it
     * was the one to do it.
     *
     * Only one subtask under a task may run at a time — a sibling still running is closed first.
     *
     * Returns the interval it created; the id is generated in here, so there is no other way for
     * the caller to know which row it is.
     */
    suspend fun startSubTask(subTaskId: String): Result<SubTaskInterval, DataError.Local>

    /**
     * Closes the subtask's open interval, banks its duration and clears its timer flag.
     *
     * The parent task keeps running — unless this subtask is what started it, which the interval
     * itself records, in which case the task is stopped again too.
     *
     * Returns the interval it closed, or null when the timer was not running.
     */
    suspend fun stopSubTask(subTaskId: String): Result<SubTaskInterval?, DataError.Local>
}
