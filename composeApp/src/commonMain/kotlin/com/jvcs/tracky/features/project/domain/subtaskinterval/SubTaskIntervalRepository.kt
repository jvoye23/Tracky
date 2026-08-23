package com.jvcs.tracky.features.project.domain.subtaskinterval

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval

interface SubTaskIntervalRepository {

    /**
     * Writes the interval locally and pushes it as a new server-side row.
     *
     * Create and update are separate entry points rather than one `upsert` for the same reason
     * IntervalRepository splits them: the caller always knows which it is — the timer opens a row
     * or closes one — while the repository cannot infer it, because by the time it is asked the
     * local row exists either way.
     */
    suspend fun createSubTaskInterval(interval: SubTaskInterval): EmptyResult<DataError>

    /** Writes the interval locally and pushes it as an update to a row the server already has. */
    suspend fun updateSubTaskInterval(interval: SubTaskInterval): EmptyResult<DataError>

    suspend fun deleteSubTaskInterval(intervalId: String): EmptyResult<DataError>
    suspend fun getOpenIntervalBySubTaskId(subTaskId: String): Result<SubTaskInterval?, DataError>

    /**
     * Drains the queued subtask-interval writes. Runs last of all five drains: this is the deepest
     * row in the tree, with no route until its subtask — and the task above that — exist on the
     * server, so ops whose subtask is still pending stay queued.
     */
    suspend fun syncPendingSubTaskIntervals()
}
