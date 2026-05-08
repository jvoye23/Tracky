package com.jvcs.tracky.features.project_tracker.presentation.task_detail

sealed interface TaskDetailAction {
    data object OnBackClick: TaskDetailAction
    data object OnToggleTimer: TaskDetailAction
    data class OnTitleChanged(val newTitle: String): TaskDetailAction
    data object OnSaveTitle: TaskDetailAction
}
