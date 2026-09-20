package com.jvcs.tracky.features.project.di

import com.jvcs.tracky.features.project.presentation.daily_overview.DailyOverviewViewModel
import com.jvcs.tracky.features.project.presentation.edit_text.EditTextTarget
import com.jvcs.tracky.features.project.presentation.edit_text.EditTextViewModel
import com.jvcs.tracky.features.project.presentation.project_detail.ProjectDetailViewModel
import com.jvcs.tracky.features.project.presentation.project_overview.ProjectOverviewViewModel
import com.jvcs.tracky.features.project.presentation.stranded_timer.StrandedTimerViewModel
import com.jvcs.tracky.features.project.presentation.timer_permission.TimerNotificationPermissionViewModel
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
        StrandedTimerViewModel(strandedTimerRepository = get())
    }

    viewModel {
        TimerNotificationPermissionViewModel(
            permissionRequester = get(),
            runningTimerRepository = get(),
            savedStateHandle = get()
        )
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

    // Same rule as below: this list must match parametersOf(...) at the edit-text nav entry.
    viewModel { (isEditMode: Boolean, projectId: String, target: EditTextTarget, taskId: String?, subTaskId: String?) ->
        EditTextViewModel(
            isEditMode = isEditMode,
            projectId = projectId,
            target = target,
            taskId = taskId,
            subTaskId = subTaskId,
            projectRepository = get(),
            projectTaskRepository = get(),
            subTaskRepository = get(),
            timeProvider = get(),
            savedStateHandle = get(),
        )
    }

    // The destructured parameter list must match parametersOf(...) at the nav entry exactly -
    // a mismatch compiles and only fails when the screen is opened.
    viewModel { (projectId: String, preselectedDateEpochDay: Long) ->
        DailyOverviewViewModel(
            projectId = projectId,
            preselectedDateEpochDay = preselectedDateEpochDay,
            projectRepository = get(),
            timeProvider = get(),
            savedStateHandle = get()
        )
    }

    viewModel { (sessionId: String) ->
        TaskDetailViewModel(
            taskId = sessionId,
            projectTaskRepository = get(),
            subTaskRepository = get(),
            projectRepository = get(),
            timeManager = get()
        )
    }
}