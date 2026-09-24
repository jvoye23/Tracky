package com.jvcs.tracky.features.project.presentation.edittext

import com.jvcs.tracky.designsystem.util.UiText

sealed interface EditTextEvent {

    data object OnSavedSuccess : EditTextEvent

    /** A new subtask was created; there is nothing left to edit here. */
    data object NavigateBack : EditTextEvent

    data class Error(val error: UiText) : EditTextEvent
}
