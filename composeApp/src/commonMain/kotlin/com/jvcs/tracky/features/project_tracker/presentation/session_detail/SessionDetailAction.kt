package com.jvcs.tracky.features.project_tracker.presentation.session_detail

sealed interface SessionDetailAction {
    data object OnBackClick: SessionDetailAction
    data object OnToggleTimer: SessionDetailAction
    data class OnTitleChanged(val newTitle: String): SessionDetailAction
    data object OnSaveTitle: SessionDetailAction
}
