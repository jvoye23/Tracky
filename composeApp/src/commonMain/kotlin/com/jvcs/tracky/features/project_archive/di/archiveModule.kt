package com.jvcs.tracky.features.project_archive.di

import com.jvcs.tracky.features.project_archive.presentation.project_archive.ProjectArchiveViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val archiveModule = module {
    viewModel {
        ProjectArchiveViewModel(
            projectRepository = get()
        )
    }
}
