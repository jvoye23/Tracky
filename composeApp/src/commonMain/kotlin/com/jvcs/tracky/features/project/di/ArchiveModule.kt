package com.jvcs.tracky.features.project.di

import com.jvcs.tracky.features.project.presentation.projectarchive.ProjectArchiveViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val archiveModule =
    module {
        viewModel {
            ProjectArchiveViewModel(
                projectRepository = get(),
                projectOrganizationRepository = get(),
            )
        }
    }
