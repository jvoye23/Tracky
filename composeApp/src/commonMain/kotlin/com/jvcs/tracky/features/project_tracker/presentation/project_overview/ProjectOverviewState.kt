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
    val filteredProjects: List<ProjectUi>? = null,
    val isGridView: Boolean = false,
    val isEditModeActive: Boolean = false,
    val selectedProjectIds: Set<String> = emptySet(),
    val isDeleteConfirmationDialogVisible: Boolean = false,
    val isSortBottomSheetVisible: Boolean = false,
    val sortOption: SortOption = SortOption.CUSTOM
    //val isSortOptionCustom: Boolean = true
)