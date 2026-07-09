package com.jvcs.tracky.features.project_archive.presentation.project_archive

sealed interface ProjectArchiveEvent {

    data object ReactivateError: ProjectArchiveEvent
}
