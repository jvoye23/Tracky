package com.jvcs.tracky.core.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jvcs.tracky.core.domain.sync.TrashCleanupScheduler
import java.util.concurrent.TimeUnit

class AndroidTrashCleanupScheduler(
    private val context: Context
) : TrashCleanupScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override suspend fun scheduleCleanup() {
        val request = PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        // KEEP so re-enqueuing on every app start is idempotent and preserves the running period.
        workManager.enqueueUniquePeriodicWork(
            TrashCleanupWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    override suspend fun cancelCleanup() {
        workManager.cancelUniqueWork(TrashCleanupWorker.WORK_NAME)
    }
}
