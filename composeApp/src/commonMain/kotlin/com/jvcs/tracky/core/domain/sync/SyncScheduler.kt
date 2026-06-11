package com.jvcs.tracky.core.domain.sync

/**
 * Platform-agnostic trigger for deferred background sync. The pending-sync queue in Room is the
 * source of truth for *what* to sync; this only schedules a drain of that queue. Android backs it
 * with WorkManager, iOS with BGTaskScheduler, JVM with a no-op.
 */
interface SyncScheduler {
    suspend fun scheduleSync()
    suspend fun cancelAllSyncs()
}
