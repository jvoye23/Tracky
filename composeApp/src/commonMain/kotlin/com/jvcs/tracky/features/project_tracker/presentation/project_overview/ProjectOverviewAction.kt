package com.jvcs.tracky.features.project_tracker.presentation.project_overview

sealed interface ProjectOverviewAction {
    data object OnStartTrackerClick: ProjectOverviewAction
    data class OnProjectCardClick (val projectId: String): ProjectOverviewAction

    data object OnToggleAddNewProjectBottomSheet: ProjectOverviewAction

    data object OnFabClick: ProjectOverviewAction
    data object OnCalendarIconClick: ProjectOverviewAction

    data class OnAddProjectClick(val projectTitle: String): ProjectOverviewAction
    data object OnMenuClick: ProjectOverviewAction
    data class OnSearchQueryChange(val query: String): ProjectOverviewAction
    data object OnToggleViewMode: ProjectOverviewAction

    data class OnProjectCardLongPress(val projectId: String): ProjectOverviewAction
    data class OnProjectCardToggleSelection(val projectId: String): ProjectOverviewAction
    data object OnExitEditMode: ProjectOverviewAction
    data object OnDeleteSelectedClick: ProjectOverviewAction
    data object OnDismissDeleteDialog: ProjectOverviewAction
    data object OnConfirmDelete: ProjectOverviewAction
    data object OnToggleSortBottomSheet: ProjectOverviewAction
    data class OnSortOptionSelected(val sortOption: SortOption): ProjectOverviewAction
}