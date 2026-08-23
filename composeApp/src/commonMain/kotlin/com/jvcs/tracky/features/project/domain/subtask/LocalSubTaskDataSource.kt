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
}
