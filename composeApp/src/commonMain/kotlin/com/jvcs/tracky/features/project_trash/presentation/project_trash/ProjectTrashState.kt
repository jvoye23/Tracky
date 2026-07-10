package com.jvcs.tracky.features.project_trash.presentation.project_trash

import com.jvcs.tracky.core.presentation.model.ProjectUi

data class ProjectTrashState(
    val projects: List<ProjectUi>? = null,
    val filteredProjects: List<ProjectUi>? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isEditModeActive: Boolean = false,
    val selectedProjectIds: Set<String> = emptySet(),
    val isDeleteConfirmationDialogVisible: Boolean = false
)
