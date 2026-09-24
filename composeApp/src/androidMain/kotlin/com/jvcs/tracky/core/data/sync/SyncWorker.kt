package com.jvcs.tracky.core.data.sync

import android.content.Context
import androidx.sqlite.SQLiteException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.domain.sync.SyncRepository
import kotlinx.io.IOException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Drains the pending-sync queue in the background. Resolves the repository from the global Koin
 * context (started in the Application) so no custom WorkerFactory / manifest changes are required.
 */
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params),
    KoinComponent {

    private val syncRepository: SyncRepository by inject()

    override suspend fun doWork(): Result =
        try {
            syncRepository.syncPendingOperations()
            Result.success()
        } catch (exception: SQLiteException) {
            Logger.withTag("SyncWorker").e(exception) { "doWork failed (SQLiteException)" }
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (exception: IOException) {
            Logger.withTag("SyncWorker").e(exception) { "doWork failed (IOException)" }
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }

    companion object {
        const val WORK_NAME = "project_pending_sync"
        const val PERIODIC_WORK_NAME = "project_periodic_sync"
        private const val MAX_RETRIES = 3
    }
}
