package com.jvcs.tracky.features.project.domain.interval

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.TaskInterval

interface IntervalRepository {

    /**
     * Writes the interval locally and pushes it as a new server-side row.
     *
     * Create and update are separate entry points rather than one `upsert` because the caller
     * always knows which it is — the timer opens a row or closes one — while the repository cannot
     * infer it: by the time it is asked, the local row exists either way.
     */
    suspend fun createTaskInterval(interval: TaskInterval): EmptyResult<DataError>

    /** Writes the interval locally and pushes it as an update to a row the server already has. */
    suspend fun updateTaskInterval(interval: TaskInterval): EmptyResult<DataError>

    suspend fun deleteTaskInterval(intervalId: String): EmptyResult<DataError>
    suspend fun getOpenIntervalByTaskId(taskId: String): Result<TaskInterval?, DataError>

    /**
     * Drains the queued interval writes. Runs last of the three drains: an interval has no route
     * until its task exists on the server, so ops whose task is still pending stay queued.
     */
    suspend fun syncPendingIntervals()
}
