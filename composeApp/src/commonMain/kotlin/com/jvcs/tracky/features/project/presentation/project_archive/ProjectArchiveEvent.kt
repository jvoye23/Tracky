package com.jvcs.tracky.features.project.presentation.project_archive

sealed interface ProjectArchiveEvent {

    data object ReactivateError: ProjectArchiveEvent
}
