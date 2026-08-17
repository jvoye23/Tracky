package com.jvcs.tracky.features.project.presentation.project_trash

import com.jvcs.tracky.features.project.presentation.models.ProjectUi

data class ProjectTrashState(
    val projects: List<ProjectUi>? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isEditModeActive: Boolean = false,
    val selectedProjectIds: Set<String> = emptySet(),
    val isDeleteConfirmationDialogVisible: Boolean = false
) {
    val filteredProjects: List<ProjectUi>?
        get() = projects?.let { list ->
            if (searchQuery.isBlank()) list
            else list.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
}
