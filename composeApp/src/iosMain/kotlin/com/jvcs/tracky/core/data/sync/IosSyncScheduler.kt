@file:OptIn(ExperimentalForeignApi::class)

package com.jvcs.tracky.core.data.sync

import com.jvcs.tracky.core.domain.sync.SyncScheduler
import kotlinx.cinterop.ExperimentalForeignApi
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

private const val SECONDS_PER_HOUR = 60.0 * 60.0
private const val SYNC_INTERVAL_HOURS = 6

/**
 * Best-effort background sync via BGTaskScheduler. To actually execute, [TASK_IDENTIFIER] must be
 * listed in Info.plist (BGTaskSchedulerPermittedIdentifiers) and a handler installed in the Swift
 * app that calls KoinHelper.runSync(...). The reactive foreground sync (ProjectSyncManager) remains
 * the dependable path on iOS.
 *
 * Submitting before that handler exists is fatal (see [BackgroundTaskRegistry]), so every submit is
 * gated on the registration having happened.
 */
class IosSyncScheduler : SyncScheduler {

    override suspend fun schedulePeriodicSync() {
        if (!BackgroundTaskRegistry.isRegistered(TASK_IDENTIFIER)) return

        val request = BGAppRefreshTaskRequest(identifier = TASK_IDENTIFIER)
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(SECONDS_PER_HOUR * SYNC_INTERVAL_HOURS)
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
    }

    // iOS can't honor a fixed 15-minute interval (BGTaskScheduler timing is OS-controlled), so app
    // start reuses the same best-effort BG refresh request. Foreground ProjectSyncManager is the
    // dependable path here.
    override suspend fun schedulePeriodicSyncOnStart() = schedulePeriodicSync()

    // Only our own request — cancelAllTaskRequests() would also drop the pending trash cleanup.
    override suspend fun cancelAllSyncs() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_IDENTIFIER)
    }

    companion object {
        const val TASK_IDENTIFIER = "com.jvcs.tracky.sync"
    }
}
