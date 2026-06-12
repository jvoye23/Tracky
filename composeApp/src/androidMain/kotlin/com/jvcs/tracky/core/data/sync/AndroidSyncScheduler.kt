package com.jvcs.tracky.core.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import java.util.concurrent.TimeUnit

class AndroidSyncScheduler(
    private val context: Context
) : SyncScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override suspend fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2000L, TimeUnit.MILLISECONDS)
            .build()

        // KEEP so rapid successive writes coalesce into a single queued sync.
        workManager.enqueueUniqueWork(SyncWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override suspend fun cancelAllSyncs() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
    }
}
