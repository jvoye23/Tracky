@file:OptIn(ExperimentalUuidApi::class)

package com.jvcs.tracky.features.project.presentation.project_overview

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.connectivity.ConnectivityObserver
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.TimerState
import com.jvcs.tracky.core.domain.util.onFailure
import com.jvcs.tracky.core.domain.util.onSuccess
import com.jvcs.tracky.features.project.presentation.mappers.toProjectUi
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import com.jvcs.tracky.features.project.presentation.util.toUiText
import com.jvcs.tracky.design_system.theme.defaultProjectColor
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.project.sortedByCustomOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(FlowPreview::class)
class ProjectOverviewViewModel(
    private val projectRepository: ProjectRepository,
    private val timeManager: TimeManager,
    private val timeProvider: TimeProvider,
    private val sessionStorage: SessionStorage,
    private val authService: AuthService,
    private val connectivityObserver: ConnectivityObserver,
    private val applicationScope: CoroutineScope
): ViewModel() {

    private val _sortOption = MutableStateFlow(SortOption.CUSTOM)
    val sortOption = _sortOption.asStateFlow()
    private val eventChannel = Channel<ProjectOverviewEvent>()

    val events = eventChannel.receiveAsFlow()
    /**
     * True from the first move of a drag until the reorder it produced is visible in the source
     * again (or is abandoned). Only while this holds may the displayed order outrank the database's.
     */
    private var reorderInFlight = false
    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(ProjectOverviewState())
    val state = combine(
        _state,
        sessionStorage.observeAuthInfo(),
        connectivityObserver.isConnected.debounce(1.seconds)
    ) { currentState, authInfo, isOnline ->
        if (authInfo == null) {
            return@combine ProjectOverviewState()
        }
        currentState.copy(
            localUser = authInfo.user,
            isOnline = isOnline
        )
    }
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                loadProjectsFromServer()
                observeProjects()
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
                // Reordering is unavailable while searching, so no drag can still be in flight.
                reorderInFlight = false
                _state.update { it.copy(searchQuery = action.query).withSectionsFromProjects() }
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
            ProjectOverviewAction.OnPinSelectedClick -> {
                pinSelectedProjects()
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
                // A different sort discards the manual order the drag was holding on to.
                reorderInFlight = false
                _sortOption.update { action.sortOption }
                _state.update { it.copy(
                    isSortBottomSheetVisible = false
                ) }
            }
            ProjectOverviewAction.OnReorderDragStart -> {
                // The card started moving: leave edit mode so it becomes a pure reorder drag.
                _state.update { it.copy(
                    isEditModeActive = false,
                    selectedProjectIds = emptySet()
                ) }
            }
            is ProjectOverviewAction.OnReorderMove -> {
                reorderInFlight = true
                _state.update { it.withReorderMove(action.fromId, action.toId) }
            }
            ProjectOverviewAction.OnReorderCancel -> {
                // Nothing was saved, so the persisted order is what [projects] already holds.
                reorderInFlight = false
                _state.update { it.withSectionsFromProjects() }
            }
            is ProjectOverviewAction.OnReorderCommit -> {
                reorderProjects(action.projectId)
            }
            is ProjectOverviewAction.OnLogoutClick -> showLogoutConfirmation()
            is ProjectOverviewAction.OnConfirmLogout -> logout()
            is ProjectOverviewAction.OnDismissLogoutConfirmation -> dismissLogoutConfirmation()
            ProjectOverviewAction.OnPullToRefresh -> refreshProjectsFromServer()
        }
    }

    /** Persists the order of the section [draggedProjectId] now sits in. */
    private fun reorderProjects(draggedProjectId: String) {
        val state = _state.value
        val section = when {
            state.pinnedProjects.any { it.projectId == draggedProjectId } -> state.pinnedProjects
            state.otherProjects.any { it.projectId == draggedProjectId } -> state.otherProjects
            else -> return
        }
        viewModelScope.launch {
            projectRepository.reorderProjects(section.map { it.projectId })
                .onFailure { dataError ->
                    // Don't leave the user looking at an order that says it saved while the snackbar
                    // says it didn't: fall back to whatever is actually persisted.
                    reorderInFlight = false
                    _state.update { it.withSectionsFromProjects() }
                    eventChannel.send(ProjectOverviewEvent.ReorderError(error = dataError.toUiText()))
                }
        }
    }

    /** Moves [fromId] to [toId]'s slot. Cross-section moves are rejected: pin state must not change. */
    private fun ProjectOverviewState.withReorderMove(fromId: String, toId: String): ProjectOverviewState = when {
        pinnedProjects.holdsBoth(fromId, toId) -> copy(pinnedProjects = pinnedProjects.moved(fromId, toId))
        otherProjects.holdsBoth(fromId, toId) -> copy(otherProjects = otherProjects.moved(fromId, toId))
        else -> this
    }

    private fun List<ProjectUi>.holdsBoth(fromId: String, toId: String) =
        any { it.projectId == fromId } && any { it.projectId == toId }

    private fun List<ProjectUi>.moved(fromId: String, toId: String): List<ProjectUi> {
        val from = indexOfFirst { it.projectId == fromId }
        val to = indexOfFirst { it.projectId == toId }
        if (from == -1 || to == -1 || from == to) return this
        return toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Folds a source emission into the section lists. While a drag is in flight the current display
     * order wins for every project that still exists ([reconciledWith]), so an emission arriving
     * mid-drag — or during the settle animation, before the reorder write has made it back through
     * Room — cannot replay the move the user just made. Once the gesture is over the source order is
     * the truth again: holding on to the display order past that point would let it override
     * reorderings the user never made in this list, such as a pin moving a card to the top of its new
     * section.
     */
    private fun ProjectOverviewState.withSectionsFrom(
        latest: List<ProjectUi>,
        adoptSourceOrder: Boolean
    ): ProjectOverviewState {
        if (adoptSourceOrder || sortOption != SortOption.CUSTOM || searchQuery.isNotBlank()) {
            return withSectionsFromProjects()
        }
        return copy(
            pinnedProjects = pinnedProjects.reconciledWith(latest.filter { it.isPinned }),
            otherProjects = otherProjects.reconciledWith(latest.filterNot { it.isPinned })
        )
    }

    /** True once the source emission carries the order already on screen: the write has landed. */
    private fun ProjectOverviewState.matchesSourceOrder(latest: List<ProjectUi>): Boolean =
        pinnedProjects.map { it.projectId } == latest.filter { it.isPinned }.map { it.projectId } &&
            otherProjects.map { it.projectId } == latest.filterNot { it.isPinned }.map { it.projectId }

    /**
     * Keeps the current display order for projects that are still present (with refreshed content),
     * drops the ones that are gone, and slots projects new to this list in at their source position
     * (e.g. a freshly created project at the top).
     */
    private fun List<ProjectUi>.reconciledWith(latest: List<ProjectUi>): List<ProjectUi> {
        val latestById = latest.associateBy { it.projectId }
        val knownIds = mapTo(HashSet()) { it.projectId }
        val common = ArrayDeque(mapNotNull { latestById[it.projectId] })
        val result = ArrayList<ProjectUi>(latest.size)
        for (item in latest) {
            if (item.projectId in knownIds) {
                if (common.isNotEmpty()) result.add(common.removeFirst())
            } else {
                result.add(item)
            }
        }
        return result
    }

    /**
     * Re-derives both section lists from [projects] — the order the repository
     * actually persisted. Used for the searched/non-custom case and to revert a cancelled drag or a
     * reorder that failed to save.
     */
    private fun ProjectOverviewState.withSectionsFromProjects(): ProjectOverviewState {
        val visible = projects.orEmpty().filterBySearchQuery(searchQuery)
        return copy(
            pinnedProjects = visible.filter { it.isPinned },
            otherProjects = visible.filterNot { it.isPinned }
        )
    }

    private fun List<ProjectUi>.filterBySearchQuery(query: String) =
        if (query.isBlank()) this else filter { it.title.contains(query, ignoreCase = true) }

    private fun List<Project>.sortedForOption(option: SortOption) = when (option) {
        SortOption.CUSTOM -> sortedByCustomOrder()
        SortOption.CREATION_DATE -> sortedByDescending { it.startDateTimeUtc }
        // lastUpdatedAt, not ownUpdatedAt: editing a task counts as modifying its project, so the
        // project moves up. The overview loads projects with their tasks, so the roll-up is real.
        SortOption.MODIFICATION_DATE -> sortedByDescending { it.lastUpdatedAt ?: it.startDateTimeUtc }
    }

    private fun archiveSelectedProjects() {
        val ids = _state.value.selectedProjectIds
        viewModelScope.launch {
            val errors = ids.mapNotNull { id ->
                (projectRepository.setProjectArchived(id, isArchived = true) as? Result.Error)?.error
            }
            _state.update { it.copy(
                isEditModeActive = false,
                selectedProjectIds = emptySet()
            ) }
            if (errors.isNotEmpty()) eventChannel.send(ProjectOverviewEvent.ArchiveError)
        }
    }

    private fun pinSelectedProjects() {
        val ids = _state.value.selectedProjectIds
        // Smart toggle: if any selected project is currently unpinned, pin all of them;
        // otherwise (all already pinned) unpin all.
        val selected = _state.value.projects?.filter { it.projectId in ids } ?: emptyList()
        val targetPinned = selected.any { !it.isPinned }
        viewModelScope.launch {
            // One call for the whole selection: the repository flips every flag and then re-indexes
            // the target section once, so the pinned projects land on top of it.
            val result = projectRepository.setProjectsPinned(ids.toList(), isPinned = targetPinned)
            _state.update { it.copy(
                isEditModeActive = false,
                selectedProjectIds = emptySet()
            ) }
            result.onFailure { eventChannel.send(ProjectOverviewEvent.PinError) }
        }
    }

    // Soft delete: stamp trashedAt with the current time so the project is moved to the trash bin
    // (and purged permanently after 30 days) instead of being removed immediately.
    private fun deleteSelectedProjects() {
        val ids = _state.value.selectedProjectIds
        viewModelScope.launch {
            // One stamp for the whole selection — they were deleted by a single user action.
            val trashedAt = timeProvider.nowInstant
            val results = ids.map { id ->
                async { projectRepository.setProjectTrashed(id, trashedAt) }
            }.awaitAll()
            results.filterIsInstance<Result.Error<DataError>>()
                .firstOrNull()
                ?.let { result ->
                eventChannel.send(ProjectOverviewEvent.AddToTrashError(result.error.toUiText()))
            }
            _state.update { it.copy(
                isEditModeActive = false,
                selectedProjectIds = emptySet(),
                isDeleteConfirmationDialogVisible = false
            ) }
        }
    }
    private fun loadProjectsFromServer() {
        viewModelScope.launch {
            projectRepository.fetchProjects()
                .onFailure { error ->
                    eventChannel.send(ProjectOverviewEvent.Error(error.toUiText()))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun refreshProjectsFromServer() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            projectRepository.fetchProjects()
                .onFailure { error ->
                    eventChannel.send(ProjectOverviewEvent.Error(error.toUiText()))
                }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeProjects() {
        // We only care whether a session exists, not what changed inside it
        sessionStorage.observeAuthInfo()
            .map { it != null }
            .distinctUntilChanged()
            // switch onto the local stream while authenticated, drop it on logout.
            // flatMapLatest cancels the previous inner flow for you, so you never
            // end up with two collectors after a re-login
            .flatMapLatest { isAuthenticated ->
                if (!isAuthenticated) emptyFlow()
                else combine(
                    projectRepository.getActiveProjects(),
                    timeManager.taskStates,
                    _sortOption
                ){ projects, activeTimers, sortOption ->
                    val runningTimer = activeTimers.entries
                        .firstOrNull() { it.value.isRunning }
                        ?.let { it.key to it.value }
                    projects.sortedForOption(sortOption)
                        .map { it.toProjectUi().withRunningTimer(runningTimer) } to sortOption
                }
            }
            .onEach { (uiProjects, sortOption) ->
                val sortChanged = _state.value.sortOption != sortOption
                val adoptSourceOrder = sortChanged || !reorderInFlight
                _state.update { state ->
                    state.copy(
                        projects = uiProjects,
                        sortOption = sortOption,
                    ).withSectionsFrom(uiProjects, adoptSourceOrder)
                }
                // The reorder has made it back through Room, so the source is authoritative again.
                if (reorderInFlight && _state.value.matchesSourceOrder(uiProjects)) {
                    reorderInFlight = false
                }
            }
            .launchIn(viewModelScope)
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
            startDateTimeUtc = timeProvider.nowInstant,
            isFinished = false,
            endDateTimeUtc = null,
            isArchived = false,
            trashedAt = null
        )


            when(val result = projectRepository.upsertProject(newProject)){
                is Result.Error -> {
                    eventChannel.send(ProjectOverviewEvent.Error(result.error.toUiText()))
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
    private fun logout() {
        _state.update { it.copy(
            showLogoutConfirmation = false
        ) }
        viewModelScope.launch {
            _state.update { it.copy(
                isLoggingOut = true
            ) }
            val authInfo = sessionStorage.observeAuthInfo().firstOrNull()
            val refreshToken = authInfo?.refreshToken ?: return@launch

            authService.logout(refreshToken)
                .onSuccess {
                    clearLocalSessionAndData()
                    eventChannel.send(ProjectOverviewEvent.OnLogoutSuccess)
                }
                .onFailure { error ->
                    eventChannel.send(ProjectOverviewEvent.OnLogoutError(error.toUiText()))
                }
        }
    }

    private fun clearLocalSessionAndData() {
        applicationScope.launch {
            sessionStorage.set(null)
            authService.clearTokenCache()
            projectRepository.deleteAllProjects()
        }
    }

    private fun showLogoutConfirmation() {
        _state.update { it.copy(
            showLogoutConfirmation = true
        ) }
    }

    private fun dismissLogoutConfirmation() {
        _state.update { it.copy(
            showLogoutConfirmation = false
        ) }
    }
}