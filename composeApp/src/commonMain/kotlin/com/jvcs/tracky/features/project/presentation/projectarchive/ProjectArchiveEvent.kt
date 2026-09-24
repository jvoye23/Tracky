package com.jvcs.tracky.features.project.presentation.projectarchive

sealed interface ProjectArchiveEvent {

    data object ReactivateError : ProjectArchiveEvent
}
