package com.jvcs.tracky.core.data.di

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.domain.notification.TimerNotificationCoordinator
import com.jvcs.tracky.core.domain.realtime.RealtimeTimerConnection
import com.jvcs.tracky.core.domain.sync.ProjectSyncManager
import com.jvcs.tracky.core.domain.sync.SyncRepository
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.sync.TrashCleanupScheduler
import com.jvcs.tracky.core.domain.sync.TrashRetention
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.di.initKoin
import com.jvcs.tracky.features.project.data.timer.StrandedTimerReconciler
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.navigation.DeepLinkRouter
import com.jvcs.tracky.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform

fun startKoinIos() {
    // initKoin already loads every module, including appModule, coreDataModule and projectModule.
    initKoin()
    val koin = KoinPlatform.getKoin()

    // Before the sync manager: a pull must not land on intervals the pass has not parked yet.
    koin.get<StrandedTimerReconciler>().start()
    koin.get<ProjectSyncManager>().start()
    // After it, so the poll is already running: the socket is the fast path, never the only one.
    koin.get<RealtimeTimerConnection>().start()
    koin.get<TimerNotificationCoordinator>().start()
    koin.get<CoroutineScope>(named("AppScope")).launch {
        koin.get<SyncScheduler>().schedulePeriodicSyncOnStart()
        koin.get<TrashCleanupScheduler>().scheduleCleanup()
    }
}

/**
 * Entry point for the Live Activity's Pause button.
 *
 * Goes through [TimerNotificationCoordinator] rather than the repositories, because the coordinator
 * owns the in-memory paused state that keeps the frozen card up once stopping the task makes the
 * running-timer flow emit null. The Android foreground service reaches the same two methods.
 *
 * [onComplete] fires once the write has settled, so the App Intent can await it before returning
 * and iOS does not redraw the card from stale state.
 */
fun onTimerNotificationPause(onComplete: () -> Unit) {
    val koin = KoinPlatform.getKoin()
    koin.get<CoroutineScope>(named("AppScope")).launch {
        try {
            koin.get<TimerNotificationCoordinator>().onPause()
        } finally {
            onComplete()
        }
    }
}

/** Entry point for the Live Activity's Play button. See [onTimerNotificationPause]. */
fun onTimerNotificationResume(onComplete: () -> Unit) {
    val koin = KoinPlatform.getKoin()
    koin.get<CoroutineScope>(named("AppScope")).launch {
        try {
            koin.get<TimerNotificationCoordinator>().onResume()
        } finally {
            onComplete()
        }
    }
}

/**
 * Entry point for a tap on the Live Activity, which opens the project being timed.
 *
 * [DeepLinkRouter] is main-thread only, and holds the route until the navigation graph attaches a
 * listener, so a tap that cold-starts the app still lands.
 */
fun openProjectFromTimerNotification(projectId: String) {
    val koin = KoinPlatform.getKoin()
    koin.get<CoroutineScope>(named("AppScope")).launch(Dispatchers.Main) {
        koin.get<DeepLinkRouter>().request(
            Route.ProjectRoute.ProjectDetail(isEditMode = false, projectId = projectId),
        )
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
        val success =
            try {
                koin
                    .get<ProjectRepository>()
                    .purgeExpiredTrashedProjects(TrashRetention.cutoff(koin.get<TimeProvider>().nowInstant))
                true
            } catch (exception: SQLiteException) {
                Logger.withTag("KoinHelper").e(exception) { "runTrashCleanup failed (SQLiteException)" }
                false
            } catch (exception: IOException) {
                Logger.withTag("KoinHelper").e(exception) { "runTrashCleanup failed (IOException)" }
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
        val success =
            try {
                koin.get<SyncRepository>().syncPendingOperations()
                true
            } catch (exception: SQLiteException) {
                Logger.withTag("KoinHelper").e(exception) { "runSync failed (SQLiteException)" }
                false
            } catch (exception: IOException) {
                Logger.withTag("KoinHelper").e(exception) { "runSync failed (IOException)" }
                false
            }
        // BGAppRefreshTask requests are one-shot; queue the next run.
        koin.get<SyncScheduler>().schedulePeriodicSync()
        onComplete(success)
    }
}
