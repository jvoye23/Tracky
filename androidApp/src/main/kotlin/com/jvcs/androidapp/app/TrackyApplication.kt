package com.jvcs.androidapp.app

import android.app.Application
import com.jvcs.tracky.core.domain.notification.TimerNotificationCoordinator
import com.jvcs.tracky.core.domain.realtime.RealtimeTimerConnection
import com.jvcs.tracky.core.domain.sync.ProjectSyncManager
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.sync.TrashCleanupScheduler
import com.jvcs.tracky.di.initKoin
import com.jvcs.tracky.features.project.data.timer.StrandedTimerReconciler
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
        // Before the sync manager: a pull must not land on intervals the pass has not parked yet.
        get<StrandedTimerReconciler>().start()
        get<ProjectSyncManager>().start()
        // After it, so the poll is already running: the socket is the fast path, never the only one.
        get<RealtimeTimerConnection>().start()
        get<TimerNotificationCoordinator>().start()
        get<CoroutineScope>(named("AppScope")).launch {
            get<SyncScheduler>().schedulePeriodicSyncOnStart()
            get<TrashCleanupScheduler>().scheduleCleanup()
        }
    }
}
