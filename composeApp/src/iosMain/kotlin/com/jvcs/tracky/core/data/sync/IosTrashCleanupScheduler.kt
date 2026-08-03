@file:OptIn(ExperimentalForeignApi::class)

package com.jvcs.tracky.core.data.sync

import com.jvcs.tracky.core.domain.sync.TrashCleanupScheduler
import kotlinx.cinterop.ExperimentalForeignApi
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

/**
 * Schedules the "empty the trash" job via BGTaskScheduler. To actually execute, [TASK_IDENTIFIER]
 * must be listed in Info.plist (BGTaskSchedulerPermittedIdentifiers) and a handler installed in the
 * Swift app that calls KoinHelper.runTrashCleanup(...). The handler re-submits the next request once
 * it finishes, since BGProcessingTask requests are one-shot.
 *
 * Submitting before that handler exists is fatal (see [BackgroundTaskRegistry]), so every submit is
 * gated on the registration having happened.
 */
class IosTrashCleanupScheduler : TrashCleanupScheduler {

    override suspend fun scheduleCleanup() {
        if (!BackgroundTaskRegistry.isRegistered(TASK_IDENTIFIER)) return

        BGTaskScheduler.sharedScheduler.getPendingTaskRequestsWithCompletionHandler { pending ->
            val alreadyScheduled = pending.orEmpty().any {
                (it as? BGTaskRequest)?.identifier == TASK_IDENTIFIER
            }
            if (alreadyScheduled) return@getPendingTaskRequestsWithCompletionHandler

            val request = BGProcessingTaskRequest(identifier = TASK_IDENTIFIER)
            request.requiresNetworkConnectivity = true
            request.requiresExternalPower = false
            // Earliest one day out; the OS decides the actual run time.
            request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(60.0 * 60.0 * 24)
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
        }
    }

    override suspend fun cancelCleanup() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_IDENTIFIER)
    }

    companion object {
        const val TASK_IDENTIFIER = "com.jvcs.tracky.trashcleanup"
    }
}
