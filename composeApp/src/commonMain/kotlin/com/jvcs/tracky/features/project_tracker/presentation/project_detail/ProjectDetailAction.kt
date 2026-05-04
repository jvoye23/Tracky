package com.jvcs.tracky.features.project_tracker.presentation.project_detail

import androidx.compose.ui.graphics.Color
import com.jvcs.tracky.features.project_tracker.domain.EditTextType

sealed interface ProjectDetailAction {

    data object OnSaveClick: ProjectDetailAction
    data class OnCreateProjectSession(val projectSessionTitle: String): ProjectDetailAction

    data object OnCloseAndCancelClick: ProjectDetailAction
    data object OnBackClick: ProjectDetailAction

    data object OnEditModeClick: ProjectDetailAction

    data class OnEditTextClick(val text: String?, val editTextType: EditTextType): ProjectDetailAction

    data object OnStartTrackerClick: ProjectDetailAction
    data class OnToggleSessionTimer(val projectSessionId: String): ProjectDetailAction
    data object OnStopTrackerClick: ProjectDetailAction

    data object OnToggleAddNewProjectSessionBottomSheet: ProjectDetailAction

    data class OnEditTextChanged(val editTextType: EditTextType, val value: String): ProjectDetailAction
    data class OnProjectSessionCardClick(val projectSessionId: String): ProjectDetailAction
    data class OnDeleteSessionClick(val sessionId: String): ProjectDetailAction
    data object OnToggleColorPicker: ProjectDetailAction
    data class OnColorChanged(val color: Color): ProjectDetailAction
    data class OnUseLightTextColorToggled(val useLightTextColor: Boolean): ProjectDetailAction
}