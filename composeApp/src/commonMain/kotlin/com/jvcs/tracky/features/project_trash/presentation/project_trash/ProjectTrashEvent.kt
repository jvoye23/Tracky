package com.jvcs.tracky.features.project_trash.presentation.project_trash

sealed interface ProjectTrashEvent {

    data object RestoreError: ProjectTrashEvent
}
