package com.jvcs.tracky.features.project.presentation.project_detail

import androidx.compose.ui.graphics.Color

sealed interface ProjectDetailAction {
    /**
     * Open the daily overview. [epochDay] is the day to preselect, or -1 to open on today -
     * the top-bar icon sends -1, a tapped "Per day" tile sends its own date.
     */
    data class OnDailyOverviewClick(val epochDay: Long) : ProjectDetailAction


    data object OnSaveClick: ProjectDetailAction
    data class OnCreateProjectSession(val projectSessionTitle: String): ProjectDetailAction

    data object OnCloseAndCancelClick: ProjectDetailAction
    data object OnBackClick: ProjectDetailAction

    data object OnEditModeClick: ProjectDetailAction

    data class OnProjectEditTextClick(val isEditMode: Boolean, val projectId: String): ProjectDetailAction

    data object OnStartTrackerClick: ProjectDetailAction
    data class OnToggleSessionTimer(val projectSessionId: String): ProjectDetailAction
    data object OnStopTrackerClick: ProjectDetailAction

    data object OnToggleAddNewProjectSessionBottomSheet: ProjectDetailAction

    data class OnProjectSessionCardClick(val projectSessionId: String): ProjectDetailAction
    data class OnDeleteSessionClick(val sessionId: String): ProjectDetailAction

    data class OnTaskCheckedChange(val taskId: String): ProjectDetailAction
    data object OnDismissUncheckTaskDialog: ProjectDetailAction

    data class OnToggleSubTaskTimer(val subTaskId: String): ProjectDetailAction
    data class OnDeleteSubTaskClick(val subTaskId: String): ProjectDetailAction
    data class OnSubTaskCheckedChange(val subTaskId: String): ProjectDetailAction

    data class OnToggleTaskExpanded(val taskId: String): ProjectDetailAction

    data class OnAddSubTaskClick(val taskId: String): ProjectDetailAction
    data class OnSubTaskTitleClick(val subTaskId: String, val currentTitle: String): ProjectDetailAction
    data object OnCommitSubTaskTitle: ProjectDetailAction
    data object OnToggleColorPicker: ProjectDetailAction
    data class OnColorChanged(val color: Color): ProjectDetailAction
    data class OnUseLightTextColorToggled(val useLightTextColor: Boolean): ProjectDetailAction
}