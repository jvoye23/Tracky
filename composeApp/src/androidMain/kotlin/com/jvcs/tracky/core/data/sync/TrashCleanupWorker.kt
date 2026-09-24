package com.jvcs.tracky.core.data.sync

import android.content.Context
import androidx.sqlite.SQLiteException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.domain.sync.TrashRetention
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.domain.project.ProjectOrganizationRepository
import kotlinx.io.IOException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Permanently deletes projects that have been in the trash longer than [TrashRetention.RETENTION],
 * locally and on the server. Resolves the repository from the global Koin context (started in the
 * Application) so no custom WorkerFactory / manifest changes are required.
 */
class TrashCleanupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params),
    KoinComponent {

    private val projectOrganizationRepository: ProjectOrganizationRepository by inject()
    private val timeProvider: TimeProvider by inject()

    override suspend fun doWork(): Result =
        try {
            projectOrganizationRepository.purgeExpiredTrashedProjects(TrashRetention.cutoff(timeProvider.nowInstant))
            Result.success()
        } catch (exception: SQLiteException) {
            Logger.withTag("TrashCleanupWorker").e(exception) { "doWork failed (SQLiteException)" }
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (exception: IOException) {
            Logger.withTag("TrashCleanupWorker").e(exception) { "doWork failed (IOException)" }
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }

    companion object {
        const val WORK_NAME = "project_trash_cleanup"
        private const val MAX_RETRIES = 3
    }
}
