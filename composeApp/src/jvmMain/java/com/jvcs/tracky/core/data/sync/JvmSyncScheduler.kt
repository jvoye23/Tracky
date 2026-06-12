package com.jvcs.tracky.core.data.sync

import com.jvcs.tracky.core.domain.sync.SyncScheduler

/** No deferred background scheduler on desktop — the reactive foreground sync handles it. */
class JvmSyncScheduler : SyncScheduler {
    override suspend fun scheduleSync() = Unit
    override suspend fun cancelAllSyncs() = Unit
}
