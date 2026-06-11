package com.jvcs.tracky.core.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Drains the pending-sync queue in the background. Resolves the repository from the global Koin
 * context (started in the Application) so no custom WorkerFactory / manifest changes are required.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val projectRepository: ProjectRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            projectRepository.syncPendingOperations()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "project_pending_sync"
        private const val MAX_RETRIES = 3
    }
}
