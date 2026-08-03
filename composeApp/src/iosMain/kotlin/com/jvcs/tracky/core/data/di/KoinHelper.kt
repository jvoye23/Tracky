package com.jvcs.tracky.core.data.di

import com.jvcs.tracky.core.domain.sync.ProjectSyncManager
import com.jvcs.tracky.core.domain.sync.SyncRepository
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.sync.TrashCleanupScheduler
import com.jvcs.tracky.core.domain.sync.TrashRetention
import com.jvcs.tracky.di.initKoin
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform

fun startKoinIos() {
    // initKoin already loads every module, including appModule, coreDataModule and projectModule.
    initKoin()
    val koin = KoinPlatform.getKoin()
    koin.get<ProjectSyncManager>().start()
    koin.get<CoroutineScope>(named("AppScope")).launch {
        koin.get<SyncScheduler>().schedulePeriodicSyncOnStart()
        koin.get<TrashCleanupScheduler>().scheduleCleanup()
    }
}

/**
 * Entry point for the iOS BGTask handler (registered in the Swift AppDelegate). Permanently purges
 * expired trashed projects, then re-schedules the next cleanup, and reports success back to the
 * BGTask via [onComplete] so it can call setTaskCompleted(success:).
 */
fun runTrashCleanup(onComplete: (Boolean) -> Unit) {
    val koin = KoinPlatform.getKoin()
    koin.get<CoroutineScope>(named("AppScope")).launch {
        val success = try {
            koin.get<ProjectRepository>().purgeExpiredTrashedProjects(TrashRetention.cutoff())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
        // BGProcessingTask requests are one-shot; queue the next run.
        koin.get<TrashCleanupScheduler>().scheduleCleanup()
        onComplete(success)
    }
}

/**
 * Entry point for the iOS background-refresh BGTask handler (registered in the Swift app). Drains
 * the pending-sync queue, then re-schedules the next refresh, and reports success back to the BGTask
 * via [onComplete] so it can call setTaskCompleted(success:).
 */
fun runSync(onComplete: (Boolean) -> Unit) {
    val koin = KoinPlatform.getKoin()
    koin.get<CoroutineScope>(named("AppScope")).launch {
        val success = try {
            koin.get<SyncRepository>().syncPendingOperations()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
        // BGAppRefreshTask requests are one-shot; queue the next run.
        koin.get<SyncScheduler>().schedulePeriodicSync()
        onComplete(success)
    }
}
