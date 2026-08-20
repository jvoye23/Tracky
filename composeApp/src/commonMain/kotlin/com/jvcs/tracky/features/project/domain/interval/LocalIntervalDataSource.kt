package com.jvcs.tracky.features.project.domain.interval

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.TaskInterval

interface LocalIntervalDataSource {
    suspend fun upsertTaskInterval(interval: TaskInterval): EmptyResult<DataError.Local>

    /**
     * Reads a single interval by id.
     *
     * The pending-sync queue stores only ids, so a queued interval op has to re-read the row from
     * local state when it finally drains — and a missing row is how the drain knows to drop the op.
     */
    suspend fun getIntervalById(intervalId: String): Result<TaskInterval?, DataError.Local>
    suspend fun getOpenIntervalByTaskId(taskId: String): Result<TaskInterval?, DataError.Local>
    suspend fun deleteTaskInterval(intervalId: String): EmptyResult<DataError.Local>
}
