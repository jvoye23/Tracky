package com.jvcs.tracky.core.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jvcs.tracky.core.domain.sync.SyncRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Drains the pending-sync queue in the background. Resolves the repository from the global Koin
 * context (started in the Application) so no custom WorkerFactory / manifest changes are required.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val syncRepository: SyncRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            syncRepository.syncPendingOperations()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }

    }

    companion object {
        const val WORK_NAME = "project_pending_sync"
        const val PERIODIC_WORK_NAME = "project_periodic_sync"
        private const val MAX_RETRIES = 3
    }
}
