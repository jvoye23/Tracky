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
    // Fired while dragging, each time the dragged card crosses a neighbour.
    data class OnReorderMove(val fromId: String, val toId: String): ProjectOverviewAction
    // Fired when the gesture is aborted without a drop: discard the preview order.
    data object OnReorderCancel: ProjectOverviewAction
    // Fired on drop. Carries the dragged card so the ViewModel can persist the section it belongs to.
    data class OnReorderCommit(val projectId: String): ProjectOverviewAction
    data object OnConfirmLogout: ProjectOverviewAction
    data object OnLogoutClick: ProjectOverviewAction
    data object OnDismissLogoutConfirmation: ProjectOverviewAction
    data object OnPullToRefresh: ProjectOverviewAction
}