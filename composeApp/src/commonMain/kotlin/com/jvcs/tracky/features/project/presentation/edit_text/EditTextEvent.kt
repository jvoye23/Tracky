package com.jvcs.tracky.features.project.presentation.edit_text

import com.jvcs.tracky.design_system.util.UiText

sealed interface EditTextEvent {
    data object OnSavedSuccess: EditTextEvent
    data class Error(val error: UiText): EditTextEvent
}