package com.jvcs.tracky.features.project_tracker.di

import com.jvcs.tracky.features.project_tracker.presentation.project_detail.ProjectDetailViewModel
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewViewModel
import com.jvcs.tracky.features.project_tracker.presentation.task_detail.TaskDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val projectModule = module {
    viewModel {
        ProjectOverviewViewModel(
            projectRepository = get(),
            projectSyncManager = get(),
            timeManager = get()
        )
    }

    viewModel { (isEdit: Boolean, projectId: String) ->
        ProjectDetailViewModel(
            isEdit = isEdit,
            projectId = projectId,
            projectRepository = get(),
            timeManager = get()
        )
    }

    viewModel { (sessionId: String) ->
        TaskDetailViewModel(
            taskId = sessionId,
            projectRepository = get(),
            timeManager = get()
        )
    }
}