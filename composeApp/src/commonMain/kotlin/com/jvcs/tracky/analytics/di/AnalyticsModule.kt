package com.jvcs.tracky.analytics.di

import com.jvcs.tracky.analytics.api.AnalyticsLogger
import com.jvcs.tracky.analytics.impl.FirebaseAnalyticsLogger
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

// The AnalyticsBackend comes from the platform: platformCoreDataModule on Android, startKoinIos on iOS.
val analyticsModule =
    module {

        singleOf(::FirebaseAnalyticsLogger) bind AnalyticsLogger::class
    }
