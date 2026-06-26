package com.jvcs.tracky.features.project_archive.presentation.project_archive

import com.jvcs.tracky.core.presentation.model.ProjectUi

data class ProjectArchiveState(
    val projects: List<ProjectUi>? = null,
    val filteredProjects: List<ProjectUi>? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = ""
)
