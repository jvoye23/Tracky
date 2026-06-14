@file:OptIn(ExperimentalForeignApi::class)

package com.jvcs.tracky.core.data.sync

import com.jvcs.tracky.core.domain.sync.SyncScheduler
import kotlinx.cinterop.ExperimentalForeignApi
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

/**
 * Best-effort background sync via BGTaskScheduler. To actually execute, the identifier must be
 * registered in Info.plist (BGTaskSchedulerPermittedIdentifiers) and a handler installed in the
 * Swift AppDelegate that resolves the repository and calls syncPendingOperations(). The reactive
 * foreground sync (ProjectSyncManager) remains the dependable path on iOS.
 */
class IosSyncScheduler : SyncScheduler {

    override suspend fun schedulePeriodicSync() {
        try {
            val request = BGAppRefreshTaskRequest(identifier = TASK_IDENTIFIER)
            request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(60.0 * 60.0 * 6 )
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
        } catch (e: Throwable) {
            // Identifier not registered / scheduler unavailable — ignore, foreground sync covers it.
        }
    }

    // iOS can't honor a fixed 15-minute interval (BGTaskScheduler timing is OS-controlled), so app
    // start reuses the same best-effort BG refresh request. Foreground ProjectSyncManager is the
    // dependable path here.
    override suspend fun schedulePeriodicSyncOnStart() = schedulePeriodicSync()

    override suspend fun cancelAllSyncs() {
        BGTaskScheduler.sharedScheduler.cancelAllTaskRequests()
    }

    companion object {
        const val TASK_IDENTIFIER = "com.jvcs.tracky.sync"
    }
}
