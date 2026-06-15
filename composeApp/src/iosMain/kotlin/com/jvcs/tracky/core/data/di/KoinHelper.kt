package com.jvcs.tracky.core.data.di

import com.jvcs.tracky.core.domain.sync.ProjectSyncManager
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.di.appModule
import com.jvcs.tracky.di.initKoin
import com.jvcs.tracky.features.project_tracker.di.projectModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform

fun startKoinIos() {
    initKoin {
        modules(
            appModule,
            coreDataModule,
            projectModule
        )
    }
    val koin = KoinPlatform.getKoin()
    koin.get<ProjectSyncManager>().start()
    koin.get<CoroutineScope>(named("AppScope")).launch {
        koin.get<SyncScheduler>().schedulePeriodicSyncOnStart()
    }
}