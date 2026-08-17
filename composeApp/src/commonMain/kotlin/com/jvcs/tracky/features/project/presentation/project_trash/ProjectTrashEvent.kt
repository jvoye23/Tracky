package com.jvcs.tracky.features.project.presentation.project_trash

sealed interface ProjectTrashEvent {

    data object RestoreError: ProjectTrashEvent
    data object HardDeleteError: ProjectTrashEvent
}
