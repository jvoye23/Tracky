package com.jvcs.tracky.core.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jvcs.tracky.core.domain.sync.TrashRetention
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Permanently deletes projects that have been in the trash longer than [TrashRetention.RETENTION],
 * locally and on the server. Resolves the repository from the global Koin context (started in the
 * Application) so no custom WorkerFactory / manifest changes are required.
 */
class TrashCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val projectRepository: ProjectRepository by inject()
    private val timeProvider: TimeProvider by inject()

    override suspend fun doWork(): Result {
        return try {
            projectRepository.purgeExpiredTrashedProjects(TrashRetention.cutoff(timeProvider.nowInstant))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "project_trash_cleanup"
        private const val MAX_RETRIES = 3
    }
}
