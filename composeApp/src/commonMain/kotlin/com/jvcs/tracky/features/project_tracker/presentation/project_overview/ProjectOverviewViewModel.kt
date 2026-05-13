@file:OptIn(ExperimentalUuidApi::class)

package com.jvcs.tracky.features.project_tracker.presentation.project_overview

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.presentation.mapper.toProjectUi
import com.jvcs.tracky.design_system.util.asUiText
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ProjectOverviewViewModel(
    private val projectRepository: ProjectRepository
): ViewModel() {

    private val _state = MutableStateFlow(ProjectOverviewState())

    private val eventChannel = Channel<ProjectOverviewEvent>()
    val events = eventChannel.receiveAsFlow()
    private var hasLoadedInitialData = false

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                getProjects()
                hasLoadedInitialData = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _state.value
        )

    fun onAction(action: ProjectOverviewAction) {
        when(action){
            is ProjectOverviewAction.OnProjectCardClick -> {}
            ProjectOverviewAction.OnStartTrackerClick -> {}
            ProjectOverviewAction.OnToggleAddNewProjectBottomSheet -> {
                _state.update { it.copy(
                    isAddNewProjectBottomSheetVisible = !it.isAddNewProjectBottomSheetVisible
                ) }
            }
            ProjectOverviewAction.OnCalendarIconClick -> {

            }
            is ProjectOverviewAction.OnAddProjectClick -> { addProject(action.projectTitle)}
            ProjectOverviewAction.OnFabClick -> {
                _state.update { it.copy(
                    isAddNewProjectBottomSheetVisible = !it.isAddNewProjectBottomSheetVisible
                ) }
            }
            ProjectOverviewAction.OnMenuClick -> { /* Handle menu */ }
            is ProjectOverviewAction.OnSearchQueryChange -> {
                _state.update { it.copy(searchQuery = action.query) }
                filterProjects(action.query)
            }
            ProjectOverviewAction.OnToggleViewMode -> {
                _state.update { it.copy(isGridView = !it.isGridView) }
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

    private fun getProjects() {
        viewModelScope.launch {
            projectRepository.getProjects()
                .collect { projectList ->
                    val uiProjects = projectList.map { it.toProjectUi() }
                    _state.update { it ->
                        it.copy(
                            projects = uiProjects,
                            filteredProjects = if (it.searchQuery.isEmpty()) uiProjects else uiProjects.filter { p -> p.title.contains(it.searchQuery, ignoreCase = true) }
                        )
                    }
                }
        }
    }

    private fun addProject(projectTitle: String) {
        viewModelScope.launch {
        val newProjectId = Uuid.random().toString()

        val newProject = Project(
            projectId = newProjectId,
            title = projectTitle,
            description = null,
            colorArgb = null,
            totalDurationMillis = null,
            startDateTimeUtc = Clock.System.now().toString(),
            isFinished = false,
            endDateTimeUtc = null,
            projectTasks = null
        )


            when(val result = projectRepository.upsertProject(newProject)){
                is Result.Error -> {
                    eventChannel.send(ProjectOverviewEvent.Error(result.error.asUiText()))
                }
                is Result.Success -> {
                    _state.update { it.copy(
                        isAddNewProjectBottomSheetVisible = false,
                        addProjectTextFieldState = TextFieldState()
                    ) }
                    eventChannel.send(ProjectOverviewEvent.NewProjectSaved(projectId = newProjectId))
                }
            }
        }
    }
}