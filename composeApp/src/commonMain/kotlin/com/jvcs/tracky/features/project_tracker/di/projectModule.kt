package com.jvcs.tracky.features.project_tracker.di

import com.jvcs.tracky.features.project_tracker.presentation.project_detail.ProjectDetailViewModel
import com.jvcs.tracky.features.project_tracker.presentation.project_detail.util.timeAndEmit
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val projectModule = module {
    viewModel {
        ProjectOverviewViewModel(
            projectRepository = get()
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
}