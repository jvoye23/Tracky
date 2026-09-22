package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result

/** Reads the server's change feed. */
interface RemoteSyncDataSource {

    /**
     * One page of changes above [since], or everything the user owns when [since] is null.
     *
     * Returns [DataError.Remote.NOT_FOUND] on a deployment that does not have the endpoint yet;
     * the caller falls back to the full-tree pull, which is what keeps this shippable ahead of
     * the backend.
     */
    suspend fun getChanges(since: Long?): Result<SyncChanges, DataError.Remote>
}
