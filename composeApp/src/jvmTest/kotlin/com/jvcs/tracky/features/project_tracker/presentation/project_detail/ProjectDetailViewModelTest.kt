@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.features.project_tracker.presentation.project_detail

import app.cash.turbine.test
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.presentation.project_detail.ProjectDetailAction
import com.jvcs.tracky.features.project.presentation.project_detail.ProjectDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val PROJECT_ID = "p1"
private const val TASK_ID = "t1"

/**
 * Covers the parent/subtask timer coupling: a task with subtasks is timed only through them, shows
 * their summed duration, and runs while any of them runs.
 */
class ProjectDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // --- helpers -------------------------------------------------------------------------------

    private fun subTask(id: String, durationMillis: Long = 0L, isFinished: Boolean = false) =
        ProjectSubTask(
            projectSubTaskId = id,
            parentProjectTaskId = TASK_ID,
            parentProjectId = PROJECT_ID,
            title = "subtask-$id",
            durationMillis = durationMillis,
            isTimerRunning = false,
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            isFinished = isFinished
        )

    private fun project(vararg subTasks: ProjectSubTask, taskDurationMillis: Long = 0L) = Project(
        projectId = PROJECT_ID,
        title = "project",
        description = null,
        colorArgb = null,
        totalDurationMillis = null,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        isFinished = false,
        endDateTimeUtc = null,
        projectTasks = listOf(
            ProjectTask(
                projectTaskId = TASK_ID,
                title = "task",
                description = null,
                durationMillis = taskDurationMillis,
                startDateTimeUtc = Instant.fromEpochMilliseconds(0),
                parentProjectId = PROJECT_ID,
                isTimerRunning = false,
                subTasks = subTasks.toList()
            )
        )
    )

    private fun TestScope.viewModel(
        project: Project,
        subTaskRepository: FakeSubTaskRepository = FakeSubTaskRepository()
    ): Pair<ProjectDetailViewModel, FakeSubTaskRepository> {
        val vm = ProjectDetailViewModel(
            isEdit = false,
            projectId = PROJECT_ID,
            projectRepository = FakeDetailProjectRepository(project),
            projectTaskRepository = FakeProjectTaskRepository(),
            subTaskRepository = subTaskRepository,
            // backgroundScope, not the test scope: the ticker is an endless loop, and a live job
            // on the test scope would keep runTest from ever completing.
            timeManager = TimeManager(backgroundScope),
            timeProvider = FakeTimeProvider(),
            ioDispatcher = dispatcher
        )
        return vm to subTaskRepository
    }

    private fun ProjectDetailViewModel.task() =
        state.value.project?.projectTasks?.first()

    /**
     * Lets pending work run without draining the scheduler.
     *
     * TimeManager's ticker is an unbounded delay loop, so once any timer is running there is always
     * another task scheduled and advanceUntilIdle() would never return.
     */
    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    // --- tests ---------------------------------------------------------------------------------

    @Test
    fun `starting a subtask marks the parent task running`() = runTest {
        val (vm, _) = viewModel(project(subTask("s1")))
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s1"))
            settle()

            val task = vm.task()!!
            assertTrue(task.isTimerRunning, "parent should run while a subtask runs")
            assertTrue(task.subTasks.first { it.projectSubTaskId == "s1" }.isTimerRunning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parent duration is the sum of its subtasks, not its own banked time`() = runTest {
        // Own time deliberately differs from the sum so we can tell which one is displayed.
        val (vm, _) = viewModel(
            project(
                subTask("s1", durationMillis = 60_000),
                subTask("s2", durationMillis = 30_000),
                taskDurationMillis = 999_000
            )
        )
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            assertEquals("00:01:30:00", vm.task()!!.displayDuration)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a task without subtasks still shows its own duration`() = runTest {
        val (vm, _) = viewModel(project(taskDurationMillis = 60_000))
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            val task = vm.task()!!
            assertTrue(task.subTasks.isEmpty())
            assertEquals(task.formattedDuration, task.displayDuration)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `starting a sibling stops the one already running`() = runTest {
        val (vm, _) = viewModel(project(subTask("s1"), subTask("s2")))
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s1"))
            settle()
            vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s2"))
            settle()

            val subTasks = vm.task()!!.subTasks
            assertFalse(
                subTasks.first { it.projectSubTaskId == "s1" }.isTimerRunning,
                "the first subtask must stop in TimeManager too, not only in the database"
            )
            assertTrue(subTasks.first { it.projectSubTaskId == "s2" }.isTimerRunning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only the running subtask accrues time, so the parent sum does not double count`() = runTest {
        val (vm, _) = viewModel(project(subTask("s1"), subTask("s2")))
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s1"))
            settle()
            vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s2"))
            advanceTimeBy(3_000)
            runCurrent()

            // s1 was stopped and reset; only s2 is ticking, so the parent must not show ~6s.
            val subTasks = vm.task()!!.subTasks
            assertEquals(1, subTasks.count { it.isTimerRunning })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parent button stops whichever subtask is running`() = runTest {
        val (vm, repo) = viewModel(project(subTask("s1")))
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s1"))
            settle()
            vm.onAction(ProjectDetailAction.OnToggleSessionTimer(TASK_ID))
            settle()

            assertFalse(vm.task()!!.isTimerRunning)
            assertEquals(listOf("s1"), repo.stopped)
            // The parent's own timer must never be touched directly.
            assertTrue(repo.started.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parent button resumes the last started subtask`() = runTest {
        val repo = FakeSubTaskRepository().apply { lastStarted = "s2" }
        val (vm, _) = viewModel(project(subTask("s1"), subTask("s2")), repo)
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnToggleSessionTimer(TASK_ID))
            settle()

            assertEquals(listOf("s2"), repo.started)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parent button falls back to the first unfinished subtask`() = runTest {
        val repo = FakeSubTaskRepository() // nothing has ever run
        val (vm, _) = viewModel(project(subTask("s1", isFinished = true), subTask("s2")), repo)
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnToggleSessionTimer(TASK_ID))
            settle()

            assertEquals(listOf("s2"), repo.started, "a finished subtask must not be resumed")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parent button does nothing when every subtask is finished`() = runTest {
        val repo = FakeSubTaskRepository()
        val (vm, _) = viewModel(project(subTask("s1", isFinished = true)), repo)
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnToggleSessionTimer(TASK_ID))
            advanceUntilIdle()

            assertTrue(repo.started.isEmpty())
            assertFalse(vm.task()!!.isTimerRunning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding a subtask writes nothing until a title is committed`() = runTest {
        val (vm, repo) = viewModel(project())
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnAddSubTaskClick(TASK_ID))
            advanceUntilIdle()
            // The server rejects a blank title, so the draft must not reach the repository.
            assertTrue(repo.upserted.isEmpty())
            assertEquals(TASK_ID, vm.state.value.pendingSubTaskParentTaskId)

            vm.state.value.editSubTaskTextFieldState.edit { replace(0, length, "Written down") }
            vm.onAction(ProjectDetailAction.OnCommitSubTaskTitle)
            advanceUntilIdle()

            assertEquals(listOf("Written down"), repo.upserted.map { it.title })
            assertNull(vm.state.value.pendingSubTaskParentTaskId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `abandoning an empty draft writes nothing`() = runTest {
        val (vm, repo) = viewModel(project())
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnAddSubTaskClick(TASK_ID))
            vm.onAction(ProjectDetailAction.OnCommitSubTaskTitle)
            advanceUntilIdle()

            assertTrue(repo.upserted.isEmpty())
            assertNull(vm.state.value.pendingSubTaskParentTaskId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// --- fakes -------------------------------------------------------------------------------------

private class FakeDetailProjectRepository(private val project: Project) : ProjectRepository {
    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? = project
    override suspend fun getProjectById(projectId: String): Project? = project

    override fun getProjects(): Flow<List<Project>> = flowOf(listOf(project))
    override fun getActiveProjects(): Flow<List<Project>> = flowOf(listOf(project))
    override fun getArchivedProjects(): Flow<List<Project>> = flowOf(emptyList())
    override fun getTrashedProjects(): Flow<List<Project>> = flowOf(emptyList())
    override suspend fun fetchProjects(): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun reorderProjects(orderedProjectIds: List<String>): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun setProjectsPinned(projectIds: List<String>, isPinned: Boolean): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun upsertProject(project: Project): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun setProjectArchived(projectId: String, isArchived: Boolean): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun setProjectTrashed(projectId: String, trashedAt: Instant?): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun purgeExpiredTrashedProjects(cutoff: Instant): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun deleteProject(projectId: String): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun deleteAllProjects() = Unit
    override suspend fun syncPendingProjects() = Unit
}

private class FakeProjectTaskRepository : ProjectTaskRepository {
    val started = mutableListOf<String>()
    val stopped = mutableListOf<String>()

    override suspend fun startProjectTask(taskId: String): EmptyResult<DataError> {
        started += taskId
        return Result.Success(Unit)
    }

    override suspend fun stopProjectTask(taskId: String): EmptyResult<DataError> {
        stopped += taskId
        return Result.Success(Unit)
    }

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun deleteProjectTask(projectId: String, taskId: String): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun updateProjectTaskDuration(taskId: String, newDurationMillis: Long): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun updateProjectTaskTitle(taskId: String, title: String): EmptyResult<DataError> = Result.Success(Unit)
    override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = flowOf(null)
    override suspend fun syncPendingTasks() = Unit
}

private class FakeSubTaskRepository : SubTaskRepository {
    val started = mutableListOf<String>()
    val stopped = mutableListOf<String>()
    val upserted = mutableListOf<ProjectSubTask>()
    val deleted = mutableListOf<String>()
    var lastStarted: String? = null

    private val subTasks = MutableStateFlow<List<ProjectSubTask>>(emptyList())

    override fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>> = subTasks

    override suspend fun upsertSubTask(subTask: ProjectSubTask): EmptyResult<DataError> {
        upserted += subTask
        subTasks.value = subTasks.value.filterNot {
            it.projectSubTaskId == subTask.projectSubTaskId
        } + subTask
        return Result.Success(Unit)
    }

    override suspend fun deleteSubTask(subTaskId: String): EmptyResult<DataError> {
        deleted += subTaskId
        return Result.Success(Unit)
    }

    override suspend fun startSubTask(subTaskId: String): EmptyResult<DataError> {
        started += subTaskId
        return Result.Success(Unit)
    }

    override suspend fun stopSubTask(subTaskId: String): EmptyResult<DataError> {
        stopped += subTaskId
        return Result.Success(Unit)
    }

    override suspend fun lastStartedSubTaskId(taskId: String): String? = lastStarted

    override suspend fun syncPendingSubTasks() = Unit
}
