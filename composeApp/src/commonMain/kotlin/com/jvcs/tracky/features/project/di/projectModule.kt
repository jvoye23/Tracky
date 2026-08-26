package com.jvcs.tracky.features.project.di

import com.jvcs.tracky.features.project.presentation.project_detail.ProjectDetailViewModel
import com.jvcs.tracky.features.project.presentation.project_overview.ProjectOverviewViewModel
import com.jvcs.tracky.features.project.presentation.task_detail.TaskDetailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val projectModule = module {

    single(named("AppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    viewModel {
        ProjectOverviewViewModel(
            projectRepository = get(),
            timeManager = get(),
            timeProvider = get(),
            sessionStorage = get(),
            authService = get(),
            connectivityObserver = get(),
            applicationScope = get(qualifier = named("AppScope"))
        )
    }

    viewModel { (isEdit: Boolean, projectId: String) ->
        ProjectDetailViewModel(
            isEdit = isEdit,
            projectId = projectId,
            projectRepository = get(),
            projectTaskRepository = get(),
            subTaskRepository = get(),
            timeManager = get(),
            timeProvider = get()
        )
    }

    viewModel { (sessionId: String) ->
        TaskDetailViewModel(
            taskId = sessionId,
            projectTaskRepository = get(),
            timeManager = get()
        )
    }
}