package com.jvcs.tracky.features.project.presentation.taskdetail

sealed interface TaskDetailAction {

    data object OnBackClick : TaskDetailAction

    data object OnToggleTimer : TaskDetailAction

    data object OnEditModeClick : TaskDetailAction

    data object OnCloseEditModeClick : TaskDetailAction

    data object OnHeaderClick : TaskDetailAction
}
