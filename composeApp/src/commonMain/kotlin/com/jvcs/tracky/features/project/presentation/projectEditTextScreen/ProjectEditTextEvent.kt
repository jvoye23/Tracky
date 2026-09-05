package com.jvcs.tracky.features.project.presentation.projectEditTextScreen

import com.jvcs.tracky.design_system.util.UiText

sealed interface ProjectEditTextEvent {
    data object OnSavedSuccess: ProjectEditTextEvent
    data class Error(val error: UiText): ProjectEditTextEvent
}