package com.jvcs.tracky.analytics.di

import com.jvcs.tracky.analytics.api.AnalyticsLogger
import com.jvcs.tracky.analytics.impl.FirebaseAnalyticsLogger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val analyticsModule =
    module {

        single { Firebase.analytics }

        singleOf(::FirebaseAnalyticsLogger) bind AnalyticsLogger::class
    }
