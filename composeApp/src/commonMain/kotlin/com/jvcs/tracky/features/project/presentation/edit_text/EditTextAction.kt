package com.jvcs.tracky.features.project.presentation.edit_text

sealed interface EditTextAction {
    data object OnEditClick : EditTextAction

    data object OnSaveClick : EditTextAction

    data object OnBackClick : EditTextAction
}
