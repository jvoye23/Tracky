package com.jvcs.tracky.features.project_tracker.presentation.project_overview

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.presentation.mapper.toProjectUi
import com.jvcs.tracky.design_system.util.asUiText
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

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


        }
    }

    private fun getProjects() {
        viewModelScope.launch {
            projectRepository.getProjects()
                .collect { projectList ->
                    _state.update { it ->
                        it.copy(
                            projects = projectList.map {
                                it.toProjectUi()
                            }
                        )
                    }
                }
        }
    }

    private fun addProject(projectTitle: String) {
        viewModelScope.launch {
        val newProjectId = Clock.System.now().toString().takeLast(6).dropLast(1)

        val newProject = Project(
            projectId = newProjectId,
            title = projectTitle,
            description = null,
            colorArgb = null,
            totalDurationMillis = null,
            startDateTimeUtc = Clock.System.now(),
            isFinished = false,
            endDateTimeUtc = null,
            projectSessions = null
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