package com.jvcs.tracky.features.project.di

import com.jvcs.tracky.features.project.presentation.projecttrash.ProjectTrashViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val trashModule =
    module {
        viewModel {
            ProjectTrashViewModel(
                projectRepository = get(),
                projectOrganizationRepository = get(),
            )
        }
    }
