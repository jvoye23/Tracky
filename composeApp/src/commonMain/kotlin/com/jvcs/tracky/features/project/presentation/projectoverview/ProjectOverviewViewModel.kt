@file:OptIn(ExperimentalUuidApi::class)

package com.jvcs.tracky.features.project.presentation.projectoverview

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.connectivity.ConnectivityObserver
import com.jvcs.tracky.core.domain.purchases.EntitlementService
import com.jvcs.tracky.core.domain.purchases.ProAccessRepository
import com.jvcs.tracky.core.domain.purchases.ProStatusProvider
import com.jvcs.tracky.core.domain.sync.CloudSyncGate
import com.jvcs.tracky.core.domain.sync.DeltaSyncApplier
import com.jvcs.tracky.core.domain.sync.LocalDataImportStore
import com.jvcs.tracky.core.domain.sync.SyncCursorStore
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.TimerState
import com.jvcs.tracky.core.domain.util.onFailure
import com.jvcs.tracky.core.domain.util.onSuccess
import com.jvcs.tracky.designsystem.theme.defaultProjectColor
import com.jvcs.tracky.designsystem.util.UiText
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatus
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatusStore
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.project.ProjectOrganizationRepository
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.project.sortedByCustomOrder
import com.jvcs.tracky.features.project.presentation.mappers.toProjectUi
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import com.jvcs.tracky.features.project.presentation.util.toUiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.error_import_refused
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(FlowPreview::class)
class ProjectOverviewViewModel(
    private val projectRepository: ProjectRepository,
    private val projectOrganizationRepository: ProjectOrganizationRepository,
    private val timeManager: TimeManager,
    private val timeProvider: TimeProvider,
    private val sessionStorage: SessionStorage,
    private val authService: AuthService,
    connectivityObserver: ConnectivityObserver,
    private val syncCursorStore: SyncCursorStore,
    private val deltaSyncApplier: DeltaSyncApplier,
    private val applicationScope: CoroutineScope,
    cloudSyncGate: CloudSyncGate,
    private val syncScheduler: SyncScheduler,
    private val onboardingStatusStore: OnboardingStatusStore,
    private val localDataImportStore: LocalDataImportStore,
    private val proAccessRepository: ProAccessRepository,
    private val entitlementService: EntitlementService,
    private val proStatusProvider: ProStatusProvider,
) : ViewModel() {

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

    /** The one entitlement refresh this screen may make; see [callServer]. */
    private var entitlementRefresh: Deferred<Boolean>? = null
    private val _state = MutableStateFlow(ProjectOverviewState())
    val state =
        combine(
            _state,
            sessionStorage.observeAuthInfo(),
            connectivityObserver.isConnected.debounce(1.seconds),
            cloudSyncGate.observeIsEnabled(),
        ) { currentState, authInfo, isOnline, isCloudSyncEnabled ->
            // No session is the free, local-only tier - the projects still show, just without an
            // account in the drawer and without anything to refresh from.
            currentState.copy(
                localUser = authInfo?.user,
                isOnline = isOnline,
                isCloudSyncEnabled = isCloudSyncEnabled,
            )
        }.onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                loadProjectsFromServer()
                observeProjects()
                observeRefusedImports()
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _state.value,
        )

    fun onAction(action: ProjectOverviewAction) {
        when (action) {
            is ProjectOverviewAction.OnProjectCardClick -> {}

            ProjectOverviewAction.OnStartTrackerClick -> {}

            ProjectOverviewAction.OnToggleAddNewProjectBottomSheet -> {
                toggleAddNewProjectBottomSheet()
            }

            ProjectOverviewAction.OnCalendarIconClick -> {
            }

            is ProjectOverviewAction.OnAddProjectClick -> {
                addProject(action.projectTitle)
            }

            ProjectOverviewAction.OnFabClick -> {
                toggleAddNewProjectBottomSheet()
            }

            ProjectOverviewAction.OnDismissProjectLimitDialog -> {
                _state.update { it.copy(isProjectLimitDialogVisible = false) }
            }

            ProjectOverviewAction.OnShowSubscriptionOffersClick -> {
                _state.update { it.copy(isProjectLimitDialogVisible = false) }
                viewModelScope.launch { eventChannel.send(ProjectOverviewEvent.NavigateToPaywall) }
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
                _state.update {
                    it.copy(
                        isEditModeActive = true,
                        selectedProjectIds = it.selectedProjectIds + action.projectId,
                    )
                }
            }

            is ProjectOverviewAction.OnProjectCardToggleSelection -> {
                _state.update {
                    val updated =
                        if (action.projectId in it.selectedProjectIds) {
                            it.selectedProjectIds - action.projectId
                        } else {
                            it.selectedProjectIds + action.projectId
                        }
                    it.copy(
                        selectedProjectIds = updated,
                        isEditModeActive = updated.isNotEmpty(),
                    )
                }
            }

            ProjectOverviewAction.OnExitEditMode -> {
                _state.update {
                    it.copy(
                        isEditModeActive = false,
                        selectedProjectIds = emptySet(),
                        isDeleteConfirmationDialogVisible = false,
                    )
                }
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
                _state.update {
                    it.copy(
                        isSortBottomSheetVisible = !it.isSortBottomSheetVisible,
                    )
                }
            }

            is ProjectOverviewAction.OnSortOptionSelected -> {
                // A different sort discards the manual order the drag was holding on to.
                reorderInFlight = false
                _sortOption.update { action.sortOption }
                _state.update {
                    it.copy(
                        isSortBottomSheetVisible = false,
                    )
                }
            }

            ProjectOverviewAction.OnReorderDragStart -> {
                // The card started moving: leave edit mode so it becomes a pure reorder drag.
                _state.update {
                    it.copy(
                        isEditModeActive = false,
                        selectedProjectIds = emptySet(),
                    )
                }
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

            is ProjectOverviewAction.OnLogoutClick -> {
                showLogoutConfirmation()
            }

            is ProjectOverviewAction.OnConfirmLogout -> {
                logout()
            }

            is ProjectOverviewAction.OnDismissLogoutConfirmation -> {
                dismissLogoutConfirmation()
            }

            ProjectOverviewAction.OnPullToRefresh -> {
                refreshProjectsFromServer()
            }
        }
    }

    /** Persists the order of the section [draggedProjectId] now sits in. */
    private fun reorderProjects(draggedProjectId: String) {
        val state = _state.value
        val section =
            when {
                state.pinnedProjects.any { it.projectId == draggedProjectId } -> state.pinnedProjects
                state.otherProjects.any { it.projectId == draggedProjectId } -> state.otherProjects
                else -> return
            }
        viewModelScope.launch {
            projectOrganizationRepository
                .reorderProjects(section.map { it.projectId })
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
    private fun ProjectOverviewState.withReorderMove(fromId: String, toId: String): ProjectOverviewState =
        when {
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
        adoptSourceOrder: Boolean,
    ): ProjectOverviewState {
        if (adoptSourceOrder || sortOption != SortOption.CUSTOM || searchQuery.isNotBlank()) {
            return withSectionsFromProjects()
        }
        return copy(
            pinnedProjects = pinnedProjects.reconciledWith(latest.filter { it.isPinned }),
            otherProjects = otherProjects.reconciledWith(latest.filterNot { it.isPinned }),
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
        val visible = projects.filterBySearchQuery(searchQuery)
        return copy(
            pinnedProjects = visible.filter { it.isPinned },
            otherProjects = visible.filterNot { it.isPinned },
        )
    }

    private fun List<ProjectUi>.filterBySearchQuery(query: String) =
        if (query.isBlank()) this else filter { it.title.contains(query, ignoreCase = true) }

    private fun List<Project>.sortedForOption(option: SortOption) =
        when (option) {
            SortOption.CUSTOM -> sortedByCustomOrder()

            SortOption.CREATION_DATE -> sortedByDescending { it.startDateTimeUtc }

            // lastUpdatedAt, not ownUpdatedAt: editing a task counts as modifying its project, so the
            // project moves up. The overview loads projects with their tasks, so the roll-up is real.
            SortOption.MODIFICATION_DATE -> sortedByDescending { it.lastUpdatedAt ?: it.startDateTimeUtc }
        }

    /** Opening the sheet is where a free user meets the project limit; closing it never checks. */
    private fun toggleAddNewProjectBottomSheet() {
        if (_state.value.isAddNewProjectBottomSheetVisible) {
            _state.update { it.copy(isAddNewProjectBottomSheetVisible = false) }
            return
        }
        viewModelScope.launch {
            if (hasReachedFreeProjectLimit()) {
                _state.update { it.copy(isProjectLimitDialogVisible = true) }
            } else {
                _state.update { it.copy(isAddNewProjectBottomSheetVisible = true) }
            }
        }
    }

    /**
     * Every stored project counts, archived and trashed ones too: only deleting one for good frees
     * a slot (Requirements/basic_free_user_flow.md §2). The local count goes first, so a Pro user
     * under the limit never waits on the store or the server.
     */
    private suspend fun hasReachedFreeProjectLimit(): Boolean =
        projectRepository.getProjects().first().size >= FREE_PROJECT_LIMIT && !proStatusProvider.isPro()

    private fun archiveSelectedProjects() {
        val ids = _state.value.selectedProjectIds
        viewModelScope.launch {
            // Archiving is Pro. The selection stays, so coming back from the paywall finds it as it was.
            if (!proStatusProvider.isPro()) {
                eventChannel.send(ProjectOverviewEvent.NavigateToPaywall)
                return@launch
            }
            val errors =
                ids.mapNotNull { id ->
                    (projectOrganizationRepository.setProjectArchived(id, isArchived = true) as? Result.Error)?.error
                }
            _state.update {
                it.copy(
                    isEditModeActive = false,
                    selectedProjectIds = emptySet(),
                )
            }
            if (errors.isNotEmpty()) eventChannel.send(ProjectOverviewEvent.ArchiveError)
        }
    }

    private fun pinSelectedProjects() {
        val ids = _state.value.selectedProjectIds
        // Smart toggle: if any selected project is currently unpinned, pin all of them;
        // otherwise (all already pinned) unpin all.
        val selected =
            _state.value.projects
                .filter { it.projectId in ids }
        val targetPinned = selected.any { !it.isPinned }
        viewModelScope.launch {
            // Pinning is Pro; unpinning stays open, so a lapsed Pro user can still clear old pins.
            if (targetPinned && !proStatusProvider.isPro()) {
                eventChannel.send(ProjectOverviewEvent.NavigateToPaywall)
                return@launch
            }
            // One call for the whole selection: the repository flips every flag and then re-indexes
            // the target section once, so the pinned projects land on top of it.
            val result = projectOrganizationRepository.setProjectsPinned(ids.toList(), isPinned = targetPinned)
            _state.update {
                it.copy(
                    isEditModeActive = false,
                    selectedProjectIds = emptySet(),
                )
            }
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
            val results =
                ids
                    .map { id ->
                        async { projectOrganizationRepository.setProjectTrashed(id, trashedAt) }
                    }.awaitAll()
            results
                .filterIsInstance<Result.Error<DataError>>()
                .firstOrNull()
                ?.let { result ->
                    eventChannel.send(ProjectOverviewEvent.AddToTrashError(result.error.toUiText()))
                }
            _state.update {
                it.copy(
                    isEditModeActive = false,
                    selectedProjectIds = emptySet(),
                    isDeleteConfirmationDialogVisible = false,
                )
            }
        }
    }

    private fun loadProjectsFromServer() {
        viewModelScope.launch {
            callServer { projectRepository.fetchProjects() }
            _state.update { it.copy(isLoading = false) }
        }
    }

    /**
     * A delta rather than a full tree, so a refresh leaves the device in the same state a synced
     * one is in: cursor advanced, recency stamped. Pulling the whole tree here did bring fresh
     * rows in, but it walked past the cursor and past [SyncRecency] both, which left a timer
     * adopted by refresh believing it had not heard from the server since launch.
     *
     * Nothing is lost by the switch: the applier falls back to the full tree whenever the feed is
     * unusable.
     */
    private fun refreshProjectsFromServer() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            callServer { deltaSyncApplier.pullChanges() }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Runs [call] and reports its failure, except for a 402 that only means the server is behind.
     *
     * The server learns about purchases from RevenueCat's webhook, so it can still say "not Pro"
     * after the store already says Pro. A lifetime purchase it did not understand stays that way
     * for good. When the store says Pro, the server is asked to re-read the entitlement and [call]
     * runs once more. A 402 from that second run is reported as it is.
     *
     * At most one refresh per screen: the server rate-limits it, and a store that says Pro while
     * RevenueCat says otherwise is not something a retry loop can fix. A 402 that arrives while the
     * refresh is still running, e.g. from a pull-to-refresh during the first load, waits for it and
     * shares its answer. A second refresh would be refused with a 429.
     */
    private suspend fun callServer(call: suspend () -> EmptyResult<DataError>) {
        val result = call()
        if (result !is Result.Error) return
        if (result.error == DataError.Remote.PAYMENT_REQUIRED && refreshStaleEntitlement()) {
            call().onFailure { error -> sendServerError(error) }
        } else {
            sendServerError(result.error)
        }
    }

    /** True when the store says Pro and the server, after a refresh, agrees. */
    private suspend fun refreshStaleEntitlement(): Boolean {
        entitlementRefresh?.let { refresh -> return !refresh.isCompleted && refresh.await() }
        val refresh =
            viewModelScope.async {
                proAccessRepository.hasProAccess() == Result.Success(true) &&
                    entitlementService.refresh() == Result.Success(true)
            }
        entitlementRefresh = refresh
        return refresh.await()
    }

    private suspend fun sendServerError(error: DataError) {
        val event =
            if (error == DataError.Remote.PAYMENT_REQUIRED) {
                ProjectOverviewEvent.ProRequired
            } else {
                ProjectOverviewEvent.Error(error.toUiText())
            }
        eventChannel.send(event)
    }

    /** Tells the user once about projects the import had to leave on this device. */
    private fun observeRefusedImports() {
        localDataImportStore
            .observeRefusedProjects()
            .filter { it.isNotEmpty() }
            .onEach { titles ->
                eventChannel.send(
                    ProjectOverviewEvent.Error(
                        UiText.Resource(Res.string.error_import_refused, arrayOf(titles.joinToString())),
                    ),
                )
                localDataImportStore.setRefusedProjects(emptyList())
            }.launchIn(viewModelScope)
    }

    private fun observeProjects() {
        // Room is the source of truth with or without a session: a free user's projects live only
        // there, and a signed-in user's arrive there through sync. Logout empties the tables, so the
        // stream empties with them.
        combine(
            projectRepository.getActiveProjects(),
            timeManager.taskStates,
            _sortOption,
        ) { projects, activeTimers, sortOption ->
            val runningTimer =
                activeTimers.entries
                    .firstOrNull { it.value.isRunning }
                    ?.let { it.key to it.value }
            projects
                .sortedForOption(sortOption)
                .map { it.toProjectUi().withRunningTimer(runningTimer) } to sortOption
        }.onEach { (uiProjects, sortOption) ->
            val sortChanged = _state.value.sortOption != sortOption
            val adoptSourceOrder = sortChanged || !reorderInFlight
            _state.update { state ->
                state
                    .copy(
                        projects = uiProjects,
                        sortOption = sortOption,
                    ).withSectionsFrom(uiProjects, adoptSourceOrder)
            }
            // The reorder has made it back through Room, so the source is authoritative again.
            if (reorderInFlight && _state.value.matchesSourceOrder(uiProjects)) {
                reorderInFlight = false
            }
        }.launchIn(viewModelScope)
    }

    private fun ProjectUi.withRunningTimer(runningTimer: Pair<String, TimerState>?): ProjectUi {
        if (runningTimer == null) return this
        val (runningTaskId, timerState) = runningTimer
        if (projectTasks?.none { it.projectTaskId == runningTaskId } == true) return this

        val updatedTasks =
            projectTasks?.map { task ->
                if (task.projectTaskId == runningTaskId) {
                    task.copy(
                        durationMillis = timerState.totalDuration.inWholeMilliseconds,
                        isTimerRunning = true,
                    )
                } else {
                    task
                }
            }
        return copy(projectTasks = updatedTasks)
    }

    private fun addProject(projectTitle: String) {
        viewModelScope.launch {
            // The sheet only opens under the limit, but another device's sync can fill it meanwhile.
            if (hasReachedFreeProjectLimit()) {
                _state.update { it.copy(isAddNewProjectBottomSheetVisible = false, isProjectLimitDialogVisible = true) }
                return@launch
            }
            val newProjectId = Uuid.random().toString()

            val newProject =
                Project(
                    projectId = newProjectId,
                    title = projectTitle,
                    description = null,
                    colorArgb = defaultProjectColor.toArgb(),
                    totalDurationMillis = null,
                    startDateTimeUtc = timeProvider.nowInstant,
                    isFinished = false,
                    endDateTimeUtc = null,
                    isArchived = false,
                    trashedAt = null,
                )

            when (val result = projectRepository.upsertProject(newProject)) {
                is Result.Error -> {
                    eventChannel.send(ProjectOverviewEvent.Error(result.error.toUiText()))
                }

                is Result.Success -> {
                    _state.update {
                        it.copy(
                            isAddNewProjectBottomSheetVisible = false,
                            addProjectTextFieldState = TextFieldState(),
                        )
                    }
                    eventChannel.send(ProjectOverviewEvent.NewProjectSaved(projectId = newProjectId))
                }
            }
        }
    }

    private fun logout() {
        _state.update {
            it.copy(
                showLogoutConfirmation = false,
            )
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoggingOut = true,
                )
            }
            val authInfo = sessionStorage.observeAuthInfo().firstOrNull()
            val refreshToken = authInfo?.refreshToken ?: return@launch

            authService
                .logout(refreshToken)
                .onSuccess {
                    clearLocalSessionAndData()
                    eventChannel.send(ProjectOverviewEvent.OnLogoutSuccess)
                }.onFailure { error ->
                    eventChannel.send(ProjectOverviewEvent.OnLogoutError(error.toUiText()))
                }
        }
    }

    private fun clearLocalSessionAndData() {
        applicationScope.launch {
            sessionStorage.set(null)
            authService.clearTokenCache()
            projectRepository.deleteAllProjects().onFailure { error ->
                // Nothing to show it on: the screen is already on its way to sign-in.
                Logger.withTag("ProjectOverviewViewModel").e { "local data not cleared on logout: $error" }
            }
            // The next account to sign in here has its own change feed; a cursor carried across
            // would silently skip everything below that sequence number.
            syncCursorStore.clear()

            // Nothing is left to drain, and a signed-out device is local-only until the next sign-in.
            syncScheduler.cancelAllSyncs()
            // Back to the start: the welcome screen offers both ways in again, free or signed in.
            onboardingStatusStore.set(OnboardingStatus.NOT_STARTED)
            // The local data just went with the account; there is nothing left to import.
            localDataImportStore.setPending(false)
            localDataImportStore.setRefusedProjects(emptyList())
        }
    }

    private fun showLogoutConfirmation() {
        _state.update {
            it.copy(
                showLogoutConfirmation = true,
            )
        }
    }

    private fun dismissLogoutConfirmation() {
        _state.update {
            it.copy(
                showLogoutConfirmation = false,
            )
        }
    }

    private companion object {
        /** Basic users may keep this many projects (Requirements/basic_free_user_flow.md §2). */
        const val FREE_PROJECT_LIMIT = 5
    }
}
