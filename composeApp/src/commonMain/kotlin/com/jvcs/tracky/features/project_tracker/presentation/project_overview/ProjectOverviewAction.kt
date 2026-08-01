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
    data object OnPinSelectedClick: ProjectOverviewAction
    data object OnArchiveSelectedClick: ProjectOverviewAction
    data object OnDeleteSelectedClick: ProjectOverviewAction
    data object OnDismissDeleteDialog: ProjectOverviewAction
    data object OnConfirmDelete: ProjectOverviewAction
    data object OnToggleSortBottomSheet: ProjectOverviewAction
    data class OnSortOptionSelected(val sortOption: SortOption): ProjectOverviewAction

    // Reorder-by-drag (Custom sort only). Fired the moment a card starts moving so edit mode is
    // dropped and the gesture becomes a pure reorder.
    data object OnReorderDragStart: ProjectOverviewAction
    // Fired on drop with the new order of the dragged section (Pinned or Other).
    data class OnReorderCommit(val orderedProjectIds: List<String>): ProjectOverviewAction
}