package com.jvcs.tracky.features.project.presentation.edittext

sealed interface EditTextAction {
    data object OnEditClick : EditTextAction

    data object OnSaveClick : EditTextAction

    data object OnBackClick : EditTextAction
}
