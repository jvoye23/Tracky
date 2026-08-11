package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult

interface SyncRepository {
    /** Drains the pending-sync queue, pushing queued creates/updates/deletes to the backend. */
    suspend fun syncPendingOperations()
}