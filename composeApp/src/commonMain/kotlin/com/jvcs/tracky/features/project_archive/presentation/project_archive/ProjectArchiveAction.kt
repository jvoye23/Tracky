package com.jvcs.tracky.features.project_archive.presentation.project_archive

sealed interface ProjectArchiveAction {
    data class OnProjectCardClick(val projectId: String): ProjectArchiveAction
    data object OnMenuClick: ProjectArchiveAction
    data object OnToggleSearch: ProjectArchiveAction
    data class OnSearchQueryChange(val query: String): ProjectArchiveAction
    data class OnProjectCardLongPress(val projectId: String): ProjectArchiveAction
    data class OnProjectCardToggleSelection(val projectId: String): ProjectArchiveAction
    data object OnExitEditMode: ProjectArchiveAction
    data object OnReactivateSelectedClick: ProjectArchiveAction
    data object OnDeleteSelectedClick: ProjectArchiveAction
    data object OnConfirmDelete: ProjectArchiveAction
    data object OnDismissDeleteDialog: ProjectArchiveAction
}
