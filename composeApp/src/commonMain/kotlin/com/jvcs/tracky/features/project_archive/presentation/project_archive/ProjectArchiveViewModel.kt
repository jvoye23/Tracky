package com.jvcs.tracky.features.project_archive.presentation.project_archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.presentation.mapper.toProjectUi
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProjectArchiveViewModel(
    private val projectRepository: ProjectRepository
): ViewModel() {

    private val _state = MutableStateFlow(ProjectArchiveState())
    private var hasLoadedInitialData = false

    private val eventChannel = Channel<ProjectArchiveEvent>()
    val events = eventChannel.receiveAsFlow()

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
                    )
                }
            }
            is ProjectArchiveAction.OnSearchQueryChange -> {
                _state.update { it.copy(searchQuery = action.query) }
            }
            is ProjectArchiveAction.OnProjectCardLongPress -> {
                _state.update { it.copy(
                    isEditModeActive = true,
                    selectedProjectIds = it.selectedProjectIds + action.projectId
                ) }
            }
            is ProjectArchiveAction.OnProjectCardToggleSelection -> {
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
            ProjectArchiveAction.OnExitEditMode -> {
                _state.update { it.copy(
                    isEditModeActive = false,
                    selectedProjectIds = emptySet(),
                    isDeleteConfirmationDialogVisible = false
                ) }
            }
            ProjectArchiveAction.OnReactivateSelectedClick -> {
                reactivateSelectedProjects()
            }
            ProjectArchiveAction.OnDeleteSelectedClick -> {
                _state.update { it.copy(isDeleteConfirmationDialogVisible = true) }
            }
            ProjectArchiveAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(isDeleteConfirmationDialogVisible = false) }
            }
            ProjectArchiveAction.OnConfirmDelete -> {
                deleteSelectedProjects()
            }
        }
    }

    private fun reactivateSelectedProjects() {
        val ids = _state.value.selectedProjectIds
        viewModelScope.launch {
            val errors = ids.mapNotNull { id ->
                (projectRepository.setProjectArchived(id, isArchived = false) as? Result.Error)?.error
            }
            _state.update { it.copy(
                isEditModeActive = false,
                selectedProjectIds = emptySet()
            ) }
            if (errors.isNotEmpty()) eventChannel.send(ProjectArchiveEvent.ReactivateError)
        }
    }

    private fun deleteSelectedProjects() {
        val ids = _state.value.selectedProjectIds
        viewModelScope.launch {
            ids.forEach { projectRepository.deleteProject(it) }
            _state.update { it.copy(
                isEditModeActive = false,
                selectedProjectIds = emptySet(),
                isDeleteConfirmationDialogVisible = false
            ) }
        }
    }

    private fun getArchivedProjects() {
        viewModelScope.launch {
            projectRepository.getArchivedProjects().collect { projectList ->
                val archived = projectList.map { it.toProjectUi() }
                _state.update { state ->
                    state.copy(
                        projects = archived
                    )
                }
            }
        }
    }
}
