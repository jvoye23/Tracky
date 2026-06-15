package com.jvcs.tracky.core.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import java.util.concurrent.TimeUnit

class AndroidSyncScheduler(
    private val context: Context
) : SyncScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override suspend fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        // KEEP so rapid successive writes coalesce into a single queued sync.
        workManager.enqueueUniquePeriodicWork(SyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override suspend fun schedulePeriodicSyncOnStart() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        // KEEP so re-enqueuing on every app start is idempotent and preserves the running period.
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    override suspend fun cancelAllSyncs() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
        workManager.cancelUniqueWork(SyncWorker.PERIODIC_WORK_NAME)
    }
}
