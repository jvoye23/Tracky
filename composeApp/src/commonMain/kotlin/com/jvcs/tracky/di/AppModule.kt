package com.jvcs.tracky.di


import com.jvcs.tracky.MainViewModel
import com.jvcs.tracky.core.domain.util.TimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {

    single(named("AppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single {
        TimeManager(
            scope = get(named("AppScope"))
        )
    }

    viewModel {
        MainViewModel(
            sessionStorage = get(),
            authService = get(),
            applicationScope = get(qualifier = named("AppScope"))
        )
    }

}