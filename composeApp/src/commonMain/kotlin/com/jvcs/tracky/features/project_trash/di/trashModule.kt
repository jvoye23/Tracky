package com.jvcs.tracky.features.project_trash.di

import com.jvcs.tracky.features.project_trash.presentation.project_trash.ProjectTrashViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val trashModule = module {
    viewModel {
        ProjectTrashViewModel(
            projectRepository = get()
        )
    }
}
