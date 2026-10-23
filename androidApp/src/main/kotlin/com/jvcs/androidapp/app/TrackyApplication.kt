package com.jvcs.androidapp.app

import android.app.Application
import com.jvcs.tracky.core.data.purchases.RevenueCatConfigurator
import com.jvcs.tracky.core.data.realtime.RealtimeTimerConnection
import com.jvcs.tracky.core.domain.notification.TimerNotificationCoordinator
import com.jvcs.tracky.core.domain.purchases.AppUserIdentitySync
import com.jvcs.tracky.core.domain.purchases.PlanTierPropertySync
import com.jvcs.tracky.core.domain.sync.CloudSyncGate
import com.jvcs.tracky.core.domain.sync.LocalDataImporter
import com.jvcs.tracky.core.domain.sync.ProjectSyncManager
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.sync.TrashCleanupScheduler
import com.jvcs.tracky.di.initKoin
import com.jvcs.tracky.features.project.data.timer.StrandedTimerReconciler
import com.jvcs.tracky.features.project.domain.project.ProjectsCountPropertySync
import com.jvcs.tracky.features.project.domain.subtask.SubTasksCountPropertySync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.qualifier.named

class TrackyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@TrackyApplication)
            androidLogger()
        }
        // First, so RevenueCat is configured with the app user id before anything waits on it.
        get<RevenueCatConfigurator>().start()

        // Before the sync manager: a pull must not land on intervals the pass has not parked yet.
        get<StrandedTimerReconciler>().start()
        get<ProjectSyncManager>().start()
        // After it, so the poll is already running: the socket is the fast path, never the only one.
        get<RealtimeTimerConnection>().start()
        get<TimerNotificationCoordinator>().start()

        // Keeps the RevenueCat customer on the signed-in account's id.
        get<AppUserIdentitySync>().start()
        // Keep the plan_tier, projects_count and sub_tasks_count analytics user properties current.
        get<PlanTierPropertySync>().start()
        get<ProjectsCountPropertySync>().start()
        get<SubTasksCountPropertySync>().start()
        // Uploads a free device's local data once it signs in to an account.
        get<LocalDataImporter>().start()

        get<CoroutineScope>(named("AppScope")).launch {
            // A local-only device has nothing to drain. Trash cleanup still runs: the purge is local.
            if (get<CloudSyncGate>().isEnabled()) get<SyncScheduler>().schedulePeriodicSyncOnStart()
            get<TrashCleanupScheduler>().scheduleCleanup()
        }
    }
}
