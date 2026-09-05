package com.jvcs.tracky.features.project.presentation.projectEditTextScreen

sealed interface ProjectEditTextAction {
    data object OnEditClick: ProjectEditTextAction
    data object OnSaveClick: ProjectEditTextAction
    data object OnBackClick: ProjectEditTextAction
}