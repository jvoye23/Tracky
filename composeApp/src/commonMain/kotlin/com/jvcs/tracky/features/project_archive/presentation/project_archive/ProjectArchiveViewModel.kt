package com.jvcs.tracky.features.project_archive.presentation.project_archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.presentation.mapper.toProjectUi
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProjectArchiveViewModel(
    private val projectRepository: ProjectRepository
): ViewModel() {

    private val _state = MutableStateFlow(ProjectArchiveState())
    private var hasLoadedInitialData = false

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                getArchivedProjects()
                hasLoadedInitialData = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _state.value
        )

    fun onAction(action: ProjectArchiveAction) {
        when (action) {
            is ProjectArchiveAction.OnProjectCardClick -> {}
            ProjectArchiveAction.OnMenuClick -> {}
            ProjectArchiveAction.OnToggleSearch -> {
                _state.update {
                    val active = !it.isSearchActive
                    it.copy(
                        isSearchActive = active,
                        searchQuery = if (active) it.searchQuery else "",
                        filteredProjects = if (active) it.filteredProjects else it.projects
                    )
                }
            }
            is ProjectArchiveAction.OnSearchQueryChange -> {
                _state.update { it.copy(searchQuery = action.query) }
                filterProjects(action.query)
            }
        }
    }

    private fun filterProjects(query: String) {
        val projects = _state.value.projects ?: return
        val filtered = if (query.isEmpty()) {
            projects
        } else {
            projects.filter { it.title.contains(query, ignoreCase = true) }
        }
        _state.update { it.copy(filteredProjects = filtered) }
    }

    private fun getArchivedProjects() {
        viewModelScope.launch {
            projectRepository.getProjects().collect { projectList ->
                val archived = projectList
                    .filter { it.isArchived }
                    .map { it.toProjectUi() }
                _state.update { state ->
                    state.copy(
                        projects = archived,
                        filteredProjects = if (state.searchQuery.isEmpty()) {
                            archived
                        } else {
                            archived.filter { it.title.contains(state.searchQuery, ignoreCase = true) }
                        }
                    )
                }
            }
        }
    }
}
