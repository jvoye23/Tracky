package com.jvcs.tracky.features.project_trash.presentation.project_trash

sealed interface ProjectTrashAction {
    data class OnProjectCardClick(val projectId: String): ProjectTrashAction
    data object OnMenuClick: ProjectTrashAction
    data object OnToggleSearch: ProjectTrashAction
    data class OnSearchQueryChange(val query: String): ProjectTrashAction
    data class OnProjectCardLongPress(val projectId: String): ProjectTrashAction
    data class OnProjectCardToggleSelection(val projectId: String): ProjectTrashAction
    data object OnExitEditMode: ProjectTrashAction
    data object OnRestoreSelectedClick: ProjectTrashAction
    data object OnDeleteSelectedClick: ProjectTrashAction
    data object OnConfirmDelete: ProjectTrashAction
    data object OnDismissDeleteDialog: ProjectTrashAction
}
