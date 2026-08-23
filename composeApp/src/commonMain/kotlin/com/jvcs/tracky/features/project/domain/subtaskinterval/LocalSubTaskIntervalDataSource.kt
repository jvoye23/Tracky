package com.jvcs.tracky.features.project.domain.subtaskinterval

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval

interface LocalSubTaskIntervalDataSource {
    suspend fun upsertSubTaskInterval(interval: SubTaskInterval): EmptyResult<DataError.Local>

    /**
     * Reads a single subtask interval by id.
     *
     * The pending-sync queue stores only ids, so a queued op has to re-read the row from local
     * state when it finally drains — and a missing row is how the drain knows to drop the op.
     */
    suspend fun getSubTaskIntervalById(intervalId: String): Result<SubTaskInterval?, DataError.Local>
    suspend fun getOpenIntervalBySubTaskId(subTaskId: String): Result<SubTaskInterval?, DataError.Local>
    suspend fun deleteSubTaskInterval(intervalId: String): EmptyResult<DataError.Local>
}
