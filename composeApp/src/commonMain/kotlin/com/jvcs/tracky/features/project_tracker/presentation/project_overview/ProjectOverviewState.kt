package com.jvcs.tracky.features.project_tracker.presentation.project_overview

import androidx.compose.foundation.text.input.TextFieldState
import com.jvcs.tracky.core.presentation.model.ProjectUi

data class ProjectOverviewState(
    val timer: Long = 0L,
    val label: String = "",
    val isAddNewProjectBottomSheetVisible: Boolean = false,
    val addProjectTextFieldState: TextFieldState = TextFieldState(),
    val searchQuery: String = "",
    val projects: List<ProjectUi>? = null,
    val isGridView: Boolean = false,
    val isEditModeActive: Boolean = false,
    val selectedProjectIds: Set<String> = emptySet(),
    val isDeleteConfirmationDialogVisible: Boolean = false,
    val isSortBottomSheetVisible: Boolean = false,
    val sortOption: SortOption = SortOption.CUSTOM,
    // The two lists the overview renders, in the order the user sees them. Derived from [projects]
    // (plus the search query) by the ViewModel and reordered there while a card is being dragged, so
    // a cancelled drag or a reorder that failed to save reverts by re-deriving them.
    val pinnedProjects: List<ProjectUi> = emptyList(),
    val otherProjects: List<ProjectUi> = emptyList()
)