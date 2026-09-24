package com.jvcs.tracky.di

import com.jvcs.tracky.MainViewModel
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.navigation.DeepLinkRouter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule =
    module {

        singleOf(::DeepLinkRouter)

        single<CoroutineDispatcher>(named("DefaultDispatcher")) { Dispatchers.Default }

        single(named("AppScope")) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }

        single {
            TimeManager(
                runningTimerRepository = get(),
                serverClock = get(),
                syncRecency = get(),
                scope = get(named("AppScope")),
            )
        }

        viewModel {
            MainViewModel(
                sessionStorage = get(),
            )
        }
    }
