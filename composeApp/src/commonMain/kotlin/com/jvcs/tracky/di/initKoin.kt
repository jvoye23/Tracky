package com.jvcs.tracky.di

import com.jvcs.tracky.core.data.di.coreDataModule
import com.jvcs.tracky.features.auth.presentation.di.authPresentationModule
import com.jvcs.tracky.features.project_tracker.di.projectModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            appModule,
            projectModule,
            coreDataModule,
            authPresentationModule
        )
    }
}
