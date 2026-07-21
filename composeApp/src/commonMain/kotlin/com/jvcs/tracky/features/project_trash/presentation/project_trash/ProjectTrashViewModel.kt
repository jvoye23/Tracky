package com.jvcs.tracky.features.project_trash.presentation.project_trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.onFailure
import com.jvcs.tracky.core.presentation.mapper.toProjectUi
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProjectTrashViewModel(
    private val projectRepository: ProjectRepository
): ViewModel() {

    private val _state = MutableStateFlow(ProjectTrashState())
    private var hasLoadedInitialData = false

    private val eventChannel = Channel<ProjectTrashEvent>()
    val events = eventChannel.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                getTrashedProjects()
                hasLoadedInitialData = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _state.value
        )

    fun onAction(action: ProjectTrashAction) {
        when (action) {
            is ProjectTrashAction.OnProjectCardClick -> {}
            ProjectTrashAction.OnMenuClick -> {}
            ProjectTrashAction.OnToggleSearch -> {
                _state.update {
                    val active = !it.isSearchActive
                    it.copy(
                        isSearchActive = active,
                        searchQuery = if (active) it.searchQuery else "",
                    )
                }
            }
            is ProjectTrashAction.OnSearchQueryChange -> {
                _state.update { it.copy(searchQuery = action.query) }
            }
            is ProjectTrashAction.OnProjectCardLongPress -> {
                _state.update { it.copy(
                    isEditModeActive = true,
                    selectedProjectIds = it.selectedProjectIds + action.projectId
                ) }
            }
            is ProjectTrashAction.OnProjectCardToggleSelection -> {
                _state.update {
                    val updated = if (action.projectId in it.selectedProjectIds) {
                        it.selectedProjectIds - action.projectId
                    } else {
                        it.selectedProjectIds + action.projectId
                    }
                    it.copy(
                        selectedProjectIds = updated,
                        isEditModeActive = updated.isNotEmpty()
                    )
                }
            }
            ProjectTrashAction.OnExitEditMode -> {
                _state.update { it.copy(
                    isEditModeActive = false,
                    selectedProjectIds = emptySet(),
                    isDeleteConfirmationDialogVisible = false
                ) }
            }
            ProjectTrashAction.OnRestoreSelectedClick -> {
                restoreSelectedProjects()
            }
            ProjectTrashAction.OnDeleteSelectedClick -> {
                _state.update { it.copy(isDeleteConfirmationDialogVisible = true) }
            }
            ProjectTrashAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(isDeleteConfirmationDialogVisible = false) }
            }
            ProjectTrashAction.OnConfirmDelete -> {
                hardDeleteSelectedProjects()
            }
        }
    }

    // Restore: clear trashedAt so the project returns to the overview.
    private fun restoreSelectedProjects() {
        val ids = _state.value.selectedProjectIds
        viewModelScope.launch {
            val results = ids.map { id ->
                async { projectRepository.setProjectTrashed(projectId = id, trashedAt = null ) }
            }.awaitAll()
            results.forEach { it.onFailure {
                eventChannel.send(ProjectTrashEvent.RestoreError)
            } }
            _state.update { it.copy(
                isEditModeActive = false,
                selectedProjectIds = emptySet()
            ) }
        }
    }

    // Permanent delete now: hard delete from the local DB and the server.
    private fun hardDeleteSelectedProjects() {
        val ids = _state.value.selectedProjectIds
        viewModelScope.launch {
            val results = ids.map { id ->
                async { projectRepository.deleteProject(id) }
            }.awaitAll()
            results.forEach { it.onFailure {
                eventChannel.send(ProjectTrashEvent.HardDeleteError)
            } }
            _state.update { it.copy(
                isEditModeActive = false,
                selectedProjectIds = emptySet(),
                isDeleteConfirmationDialogVisible = false
            ) }
        }
    }

    private fun filterProjects(query: String) {
        val projects = _state.value.projects ?: return
        val filtered = if (query.isEmpty()) {
            projects
        } else {
            projects.filter { it.title.contains(query, ignoreCase = true) }
        }
    }

    private fun getTrashedProjects() {
        viewModelScope.launch {
            projectRepository.getTrashedProjects().collect { projectList ->
                val trashed = projectList.map { it.toProjectUi() }
                _state.update { state ->
                    state.copy(
                        projects = trashed,
                    )
                }
            }
        }
    }
}
