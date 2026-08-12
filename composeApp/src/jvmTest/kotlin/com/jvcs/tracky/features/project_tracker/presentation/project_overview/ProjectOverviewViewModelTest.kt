@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.features.project_tracker.presentation.project_overview

import app.cash.turbine.test
import com.jvcs.tracky.core.domain.auth.FakeAuthService
import com.jvcs.tracky.core.domain.auth.FakeSessionStorage
import com.jvcs.tracky.core.domain.connectivity.ConnectivityObserver
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.design_system.util.UiText
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.error_disk_full
import tracky.composeapp.generated.resources.error_no_internet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ProjectOverviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Pinned: p1, p2 (indices 0, 1). Other: p3, p4 (indices 0, 1). */
    private fun seededProjects() = listOf(
        project("p1", isPinned = true, sortIndex = 0),
        project("p2", isPinned = true, sortIndex = 1),
        project("p3", sortIndex = 0),
        project("p4", sortIndex = 1)
    )

    private fun project(id: String, isPinned: Boolean = false, sortIndex: Long? = null) = Project(
        projectId = id,
        title = "title-$id",
        description = null,
        colorArgb = null,
        totalDurationMillis = null,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        isFinished = false,
        endDateTimeUtc = null,
        isPinned = isPinned,
        sortIndex = sortIndex
    )

    /** Builds the ViewModel without subscribing to [state], so its `onStart` has not run yet. */
    private fun TestScope.createViewModel(
        repository: FakeProjectRepository,
        sessionStorage: FakeSessionStorage = FakeSessionStorage(),
        authService: FakeAuthService = FakeAuthService(),
    ): ProjectOverviewViewModel = ProjectOverviewViewModel(
        projectRepository = repository,
        timeManager = TimeManager(CoroutineScope(dispatcher)),
        timeProvider = FakeTimeProvider(),
        sessionStorage = sessionStorage,
        authService = authService,
        // An `expect class`, so it cannot be faked; the JVM actual is already always-connected.
        connectivityObserver = ConnectivityObserver(),
        // Shares the test scheduler, so `advanceUntilIdle` drives the logout teardown and the
        // scope dies with the test instead of outliving it.
        applicationScope = backgroundScope,
    )

    /**
     * Subscribes to [state], which starts its `WhileSubscribed` pipeline. `state`'s `onStart` runs
     * `loadProjectsFromServer()`, so call this *inside* a `viewModel.events.test { }` block whenever
     * the test asserts on what that initial load reports — the event channel is a rendezvous
     * channel, and a send with no collector attached parks instead of arriving.
     */
    private fun TestScope.startCollectingState(viewModel: ProjectOverviewViewModel) {
        backgroundScope.launch { viewModel.state.collect { } }
        testScheduler.advanceUntilIdle()
    }

    /** Starts the ViewModel with [state] collected, so its `WhileSubscribed` pipeline is running. */
    private fun TestScope.viewModelWith(
        repository: FakeProjectRepository,
        sessionStorage: FakeSessionStorage = FakeSessionStorage(),
        authService: FakeAuthService = FakeAuthService(),
    ): ProjectOverviewViewModel =
        createViewModel(repository, sessionStorage, authService).also { startCollectingState(it) }

    /** The state is a `stateIn` flow, so let the dispatcher deliver pending updates before reading it. */
    private fun TestScope.stateOf(viewModel: ProjectOverviewViewModel): ProjectOverviewState {
        testScheduler.advanceUntilIdle()
        return viewModel.state.value
    }

    @Test
    fun state_splitsProjectsIntoSections() = runTest(dispatcher) {
        val viewModel = viewModelWith(FakeProjectRepository(seededProjects()))

        assertEquals(listOf("p1", "p2"), stateOf(viewModel).pinnedProjects.map { it.projectId })
        assertEquals(listOf("p3", "p4"), stateOf(viewModel).otherProjects.map { it.projectId })
    }

    @Test
    fun onReorderMove_reordersWithinASection() = runTest(dispatcher) {
        val viewModel = viewModelWith(FakeProjectRepository(seededProjects()))

        viewModel.onAction(ProjectOverviewAction.OnReorderMove(fromId = "p4", toId = "p3"))

        assertEquals(listOf("p4", "p3"), stateOf(viewModel).otherProjects.map { it.projectId })
        assertEquals(listOf("p1", "p2"), stateOf(viewModel).pinnedProjects.map { it.projectId })
    }

    @Test
    fun onReorderMove_ignoresCrossSectionMoves() = runTest(dispatcher) {
        val viewModel = viewModelWith(FakeProjectRepository(seededProjects()))

        // Dragging an unpinned card onto a pinned one must not change either section — pin state is
        // not something a reorder is allowed to decide.
        viewModel.onAction(ProjectOverviewAction.OnReorderMove(fromId = "p3", toId = "p1"))

        assertEquals(listOf("p1", "p2"), stateOf(viewModel).pinnedProjects.map { it.projectId })
        assertEquals(listOf("p3", "p4"), stateOf(viewModel).otherProjects.map { it.projectId })
    }

    @Test
    fun onReorderCancel_revertsToThePersistedOrder() = runTest(dispatcher) {
        val viewModel = viewModelWith(FakeProjectRepository(seededProjects()))

        viewModel.onAction(ProjectOverviewAction.OnReorderMove(fromId = "p4", toId = "p3"))
        viewModel.onAction(ProjectOverviewAction.OnReorderCancel)

        assertEquals(listOf("p3", "p4"), stateOf(viewModel).otherProjects.map { it.projectId })
    }

    @Test
    fun onReorderCommit_whenWriteFails_revertsTheOrder_andReportsTheError() = runTest(dispatcher) {
        val repository = FakeProjectRepository(seededProjects()).apply {
            reorderResult = Result.Error(DataError.Local.DISK_FULL)
        }
        val viewModel = viewModelWith(repository)

        viewModel.events.test {
            viewModel.onAction(ProjectOverviewAction.OnReorderMove(fromId = "p4", toId = "p3"))
            viewModel.onAction(ProjectOverviewAction.OnReorderCommit("p4"))
            testScheduler.advanceUntilIdle()

            assertEquals(
                ProjectOverviewEvent.ReorderError(UiText.Resource(Res.string.error_disk_full)),
                awaitItem()
            )
            expectNoEvents()
        }

        // The snackbar must not contradict the list: both say the reorder did not happen.
        assertEquals(listOf("p3", "p4"), stateOf(viewModel).otherProjects.map { it.projectId })
    }

    @Test
    fun loadProjectsFromServer_whenOffline_reportsNoInternet_ratherThanUnknown() = runTest(dispatcher) {
        val repository = FakeProjectRepository(seededProjects()).apply {
            fetchResult = Result.Error(DataError.Remote.NO_INTERNET)
        }
        val viewModel = createViewModel(repository)

        viewModel.events.test {
            startCollectingState(viewModel) // `state`'s `onStart` runs `loadProjectsFromServer()`.

            // The user must be told the connection is the problem, not shown "an unknown error happened".
            assertEquals(
                ProjectOverviewEvent.Error(UiText.Resource(Res.string.error_no_internet)),
                awaitItem()
            )
            expectNoEvents()
        }

        // A failed pull still has to clear the spinner, or the screen hangs on the loading state.
        assertFalse(stateOf(viewModel).isLoading)
    }

    @Test
    fun loadProjectsFromServer_whenItSucceeds_reportsNothing_andClearsLoading() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeProjectRepository(seededProjects()))

        viewModel.events.test {
            startCollectingState(viewModel)
            expectNoEvents()
        }

        assertFalse(stateOf(viewModel).isLoading)
    }

    @Test
    fun onReorderCommit_whenWriteSucceeds_keepsTheNewOrder_evenBeforeTheSourceCatchesUp() = runTest(dispatcher) {
        val repository = FakeProjectRepository(seededProjects())
        val viewModel = viewModelWith(repository)

        viewModel.onAction(ProjectOverviewAction.OnReorderMove(fromId = "p4", toId = "p3"))
        viewModel.onAction(ProjectOverviewAction.OnReorderCommit("p4"))
        testScheduler.advanceUntilIdle()

        // The commit carries only the dragged card; the section order comes from the ViewModel.
        assertEquals(listOf(listOf("p4", "p3")), repository.reorderCalls)

        // The DB re-emits the pre-reorder order while the write is still settling; replaying it would
        // undo the move the user just made in front of their eyes.
        repository.emit(seededProjects())
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("p4", "p3"), stateOf(viewModel).otherProjects.map { it.projectId })
    }

    @Test
    fun newProject_appearsAtTheTop_withoutDisturbingTheReorderedOrder() = runTest(dispatcher) {
        val repository = FakeProjectRepository(seededProjects())
        val viewModel = viewModelWith(repository)

        viewModel.onAction(ProjectOverviewAction.OnReorderMove(fromId = "p4", toId = "p3"))
        repository.emit(seededProjects() + project("p5")) // no sortIndex yet -> sorts first
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("p5", "p4", "p3"), stateOf(viewModel).otherProjects.map { it.projectId })
    }

    @Test
    fun pinnedProject_movesToTheTopOfItsNewSection_afterTheReindexArrives() = runTest(dispatcher) {
        val repository = FakeProjectRepository(seededProjects())
        val viewModel = viewModelWith(repository)

        // A pin lands in two emissions: the flag flip first, where p4 still carries its old index and
        // ties with p2, then the re-index that actually puts it on top.
        repository.emit(
            listOf(
                project("p1", isPinned = true, sortIndex = 0),
                project("p2", isPinned = true, sortIndex = 1),
                project("p4", isPinned = true, sortIndex = 1),
                project("p3", sortIndex = 0)
            )
        )
        repository.emit(
            listOf(
                project("p4", isPinned = true, sortIndex = 0),
                project("p1", isPinned = true, sortIndex = 1),
                project("p2", isPinned = true, sortIndex = 2),
                project("p3", sortIndex = 0)
            )
        )
        testScheduler.advanceUntilIdle()

        // Holding on to the first emission's order would strand the pinned card at the bottom.
        assertEquals(listOf("p4", "p1", "p2"), stateOf(viewModel).pinnedProjects.map { it.projectId })
    }

    @Test
    fun searchQuery_filtersBothSections() = runTest(dispatcher) {
        val viewModel = viewModelWith(FakeProjectRepository(seededProjects()))

        viewModel.onAction(ProjectOverviewAction.OnSearchQueryChange("p3"))

        assertTrue(stateOf(viewModel).pinnedProjects.isEmpty())
        assertEquals(listOf("p3"), stateOf(viewModel).otherProjects.map { it.projectId })
    }

    @Test
    fun onPinSelectedClick_pinsTheWholeSelectionInOneCall() = runTest(dispatcher) {
        val repository = FakeProjectRepository(seededProjects())
        val viewModel = viewModelWith(repository)

        viewModel.onAction(ProjectOverviewAction.OnProjectCardLongPress("p3"))
        viewModel.onAction(ProjectOverviewAction.OnProjectCardToggleSelection("p4"))
        viewModel.onAction(ProjectOverviewAction.OnPinSelectedClick)
        testScheduler.advanceUntilIdle()

        // One gesture, one repository call — the repository re-indexes the target section once.
        assertEquals(1, repository.pinCalls.size)
        val (ids, isPinned) = repository.pinCalls.single()
        assertEquals(setOf("p3", "p4"), ids.toSet())
        assertTrue(isPinned)
    }
}

// --- Fake -----------------------------------------------------------------------------------------

private class FakeProjectRepository(initial: List<Project>) : ProjectRepository {
    private val projectsFlow = MutableStateFlow(initial)
    var reorderResult: EmptyResult<DataError> = Result.Success(Unit)
    var fetchResult: Result<List<Project>, DataError.Remote> = Result.Success(emptyList())
    val reorderCalls = mutableListOf<List<String>>()
    val pinCalls = mutableListOf<Pair<List<String>, Boolean>>()

    fun emit(projects: List<Project>) { projectsFlow.value = projects }

    override fun getProjects(): Flow<List<Project>> = projectsFlow

    override fun getActiveProjects(): Flow<List<Project>> = projectsFlow
        .map { projects -> projects.filter { !it.isArchived && !it.isFinished && it.trashedAt == null } }

    override suspend fun fetchProjects(): Result<List<Project>, DataError.Remote> = fetchResult

    override suspend fun reorderProjects(orderedProjectIds: List<String>): EmptyResult<DataError> {
        reorderCalls += orderedProjectIds
        return reorderResult
    }

    override suspend fun setProjectsPinned(projectIds: List<String>, isPinned: Boolean): EmptyResult<DataError> {
        pinCalls += projectIds to isPinned
        return Result.Success(Unit)
    }

    override fun getArchivedProjects(): Flow<List<Project>> = flowOf(emptyList())
    override fun getTrashedProjects(): Flow<List<Project>> = flowOf(emptyList())
    override suspend fun getProjectById(projectId: String): Project? = projectsFlow.value.find { it.projectId == projectId }
    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? = getProjectById(projectId)
    override suspend fun upsertProject(project: Project): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun setProjectArchived(projectId: String, isArchived: Boolean): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun setProjectTrashed(projectId: String, trashedAt: Instant?): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun purgeExpiredTrashedProjects(cutoff: Instant): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun deleteProject(projectId: String): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun deleteProjectTask(taskId: String) = Unit
    override suspend fun deleteAllProjects() = Unit
    override suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long) = Unit
    override fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = flowOf(null)
    override suspend fun upsertTaskInterval(interval: TaskInterval) = Unit
    override suspend fun getOpenIntervalByTaskId(taskId: String): TaskInterval? = null
    override suspend fun startTask(taskId: String) = Unit
    override suspend fun stopTask(taskId: String) = Unit
    override suspend fun updateTaskTitle(taskId: String, title: String) = Unit
}
