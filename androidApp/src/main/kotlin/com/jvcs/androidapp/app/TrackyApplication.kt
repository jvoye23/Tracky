package com.jvcs.androidapp.app

import android.app.Application
import com.jvcs.tracky.core.domain.sync.ProjectSyncManager
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.di.initKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.qualifier.named



class TrackyApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@TrackyApplication)
            androidLogger()
        }
        get<ProjectSyncManager>().start()
        get<CoroutineScope>(named("AppScope")).launch {
            get<SyncScheduler>().schedulePeriodicSyncOnStart()
        }
    }
}