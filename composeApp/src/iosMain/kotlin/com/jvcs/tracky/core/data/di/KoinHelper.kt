package com.jvcs.tracky.core.data.di

import com.jvcs.tracky.di.appModule
import com.jvcs.tracky.di.initKoin
import com.jvcs.tracky.features.project_tracker.di.projectModule

fun startKoinIos() {
    initKoin {
        modules(
            appModule,
            coreDataModule,
            projectModule
        )
    }
}