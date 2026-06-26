@file:OptIn(ExperimentalUuidApi::class)

package com.jvcs.tracky.features.project_tracker.presentation.project_overview

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.sync.ProjectSyncManager
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.core.domain.util.TimerState
import com.jvcs.tracky.core.presentation.mapper.toProjectUi
import com.jvcs.tracky.core.presentation.model.ProjectUi
import com.jvcs.tracky.design_system.theme.defaultProjectColor
import com.jvcs.tracky.design_system.util.asUiText
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ProjectOverviewViewModel(
    private val projectRepository: ProjectRepository,
    private val projectSyncManager: ProjectSyncManager,
    private val timeManager: TimeManager
): ViewModel() {

    private val _state = MutableStateFlow(ProjectOverviewState())
    private val _sortOption = MutableStateFlow(SortOption.CUSTOM)
    val sortOption = _sortOption.asStateFlow()

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
            is ProjectOverviewAction.OnProjectCardLongPress -> {
                _state.update { it.copy(
                    isEditModeActive = true,
                    selectedProjectIds = it.selectedProjectIds + action.projectId
                ) }
            }
            is ProjectOverviewAction.OnProjectCardToggleSelection -> {
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
            ProjectOverviewAction.OnExitEditMode -> {
                _state.update { it.copy(
                    isEditModeActive = false,
                    selectedProjectIds = emptySet(),
                    isDeleteConfirmationDialogVisible = false
                ) }
            }
            ProjectOverviewAction.OnArchiveSelectedClick -> {
                archiveSelectedProjects()
            }
            ProjectOverviewAction.OnDeleteSelectedClick -> {
                _state.update { it.copy(isDeleteConfirmationDialogVisible = true) }
            }
            ProjectOverviewAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(isDeleteConfirmationDialogVisible = false) }
            }
            ProjectOverviewAction.OnConfirmDelete -> {
                deleteSelectedProjects()
            }
            ProjectOverviewAction.OnToggleSortBottomSheet -> {
                _state.update { it.copy(
                    isSortBottomSheetVisible = !it.isSortBottomSheetVisible
                ) }
            }
            is ProjectOverviewAction.OnSortOptionSelected -> {
                _sortOption.update { action.sortOption }
                _state.update { it.copy(
                    isSortBottomSheetVisible = false
                ) }
            }
        }
    }

    private fun List<Project>.sortedForOption(option: SortOption) = when (option) {
        SortOption.CUSTOM -> this
        SortOption.CREATION_DATE -> sortedByDescending { it.startDateTimeUtc }
        SortOption.MODIFICATION_DATE -> sortedByDescending { it.updatedAt ?: it.startDateTimeUtc }
    }

    private fun archiveSelectedProjects() {
        val ids = _state.value.selectedProjectIds
        viewModelScope.launch {
            ids.forEach { id ->
                projectRepository.setProjectArchived(id, isArchived = true)
            }
            _state.update { it.copy(
                isEditModeActive = false,
                selectedProjectIds = emptySet()
            ) }
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
            combine(
                projectRepository.getProjects(),
                timeManager.taskStates,
                _sortOption
            ) { projectList, activeTimers, sortOption ->
                _state.update { it.copy(
                    sortOption = sortOption
                ) }
                // Only one timer can run at a time across all tasks/projects.
                val runningTimer = activeTimers.entries
                    .firstOrNull { it.value.isRunning }
                    ?.let { it.key to it.value }
                projectList
                    .filter { !it.isArchived }
                    .sortedForOption(sortOption)
                    .map { it.toProjectUi().withRunningTimer(runningTimer) }
            }.collect { uiProjects ->
                _state.update { state ->
                    state.copy(
                        projects = uiProjects,
                        filteredProjects = if (state.searchQuery.isEmpty()) uiProjects else uiProjects.filter { p -> p.title.contains(state.searchQuery, ignoreCase = true) }
                    )
                }
            }
        }
    }

    private fun ProjectUi.withRunningTimer(runningTimer: Pair<String, TimerState>?): ProjectUi {
        if (runningTimer == null) return this
        val (runningTaskId, timerState) = runningTimer
        if (projectTasks?.none { it.id == runningTaskId } == true) return this

        val updatedTasks = projectTasks?.map { task ->
            if (task.id == runningTaskId) {
                task.copy(
                    formattedDuration = timerState.formattedTime,
                    isTimerRunning = true
                )
            } else {
                task
            }
        }
        return copy(projectTasks = updatedTasks)
    }

    private fun addProject(projectTitle: String) {
        viewModelScope.launch {
        val newProjectId = Uuid.random().toString()

        val newProject = Project(
            projectId = newProjectId,
            title = projectTitle,
            description = null,
            colorArgb = defaultProjectColor.toArgb(),
            totalDurationMillis = null,
            startDateTimeUtc = Clock.System.now(),
            isFinished = false,
            endDateTimeUtc = null,
            isArchived = false,
            trashedAt = null
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