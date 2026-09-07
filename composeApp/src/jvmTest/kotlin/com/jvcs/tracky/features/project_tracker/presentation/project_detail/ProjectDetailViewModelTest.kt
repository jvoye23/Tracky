@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.features.project_tracker.presentation.project_detail

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import kotlinx.coroutines.flow.map
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

    private fun project(
        vararg subTasks: ProjectSubTask,
        taskDurationMillis: Long = 0L,
        taskFinished: Boolean = false
    ) = Project(
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
                isFinished = taskFinished,
                subTasks = subTasks.toList()
            )
        )
    )

    private fun TestScope.viewModel(
        project: Project,
        subTaskRepository: FakeSubTaskRepository = FakeSubTaskRepository(),
        // Seeded from the project by default: setTaskFinished reads the task row back before
        // writing, so a repository that answers null would make every finish silently no-op.
        taskRepository: FakeProjectTaskRepository =
            FakeProjectTaskRepository(project.projectTasks?.firstOrNull()),
        projectRepository: FakeDetailProjectRepository = FakeDetailProjectRepository(project)
    ): Pair<ProjectDetailViewModel, FakeSubTaskRepository> {
        val vm = ProjectDetailViewModel(
            isEdit = false,
            projectId = PROJECT_ID,
            projectRepository = projectRepository,
            projectTaskRepository = taskRepository,
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

    @Test
    fun `checking a subtask marks it finished in state without a reload`() = runTest {
        // The screen loads its project once, so the flip has to land in state itself or the card
        // keeps rendering the stale row until the user leaves and comes back.
        val s1 = subTask("s1")
        val (vm, repo) = viewModel(project(s1, subTask("s2")), FakeSubTaskRepository(listOf(s1)))
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnSubTaskCheckedChange("s1"))
            advanceUntilIdle()

            val task = vm.task()!!
            assertTrue(task.subTasks.first { it.projectSubTaskId == "s1" }.isFinished)
            assertEquals(1, task.doneSubTaskCount, "the progress row reads off state too")
            assertTrue(repo.upserted.last().isFinished)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unchecking a finished subtask clears it in state`() = runTest {
        val s1 = subTask("s1", isFinished = true)
        val (vm, repo) = viewModel(project(s1), FakeSubTaskRepository(listOf(s1)))
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnSubTaskCheckedChange("s1"))
            advanceUntilIdle()

            assertFalse(vm.task()!!.subTasks.first().isFinished)
            assertFalse(repo.upserted.last().isFinished)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- task checkbox ---------------------------------------------------------------------

    @Test
    fun `checking a task without subtasks finishes it`() = runTest {
        val taskRepo = FakeProjectTaskRepository(project().projectTasks!!.first())
        val (vm, _) = viewModel(project(), taskRepository = taskRepo)
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
            advanceUntilIdle()

            assertTrue(vm.task()!!.isFinished)
            assertTrue(taskRepo.upserted.last().isFinished)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checking a running task without subtasks stops its timer`() = runTest {
        val taskRepo = FakeProjectTaskRepository(project().projectTasks!!.first())
        val (vm, _) = viewModel(project(), taskRepository = taskRepo)
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnToggleSessionTimer(TASK_ID))
            settle()
            assertTrue(vm.task()!!.isTimerRunning, "precondition: the timer is running")

            vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
            settle()

            assertTrue(taskRepo.stopped.contains(TASK_ID), "a finished task must not keep counting")
            assertFalse(vm.task()!!.isTimerRunning)
            assertTrue(vm.task()!!.isFinished)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unchecking a task without subtasks clears it`() = runTest {
        val finished = project(taskFinished = true)
        val taskRepo = FakeProjectTaskRepository(finished.projectTasks!!.first())
        val (vm, _) = viewModel(finished, taskRepository = taskRepo)
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
            advanceUntilIdle()

            assertFalse(vm.task()!!.isFinished, "a task with no subtasks toggles freely")
            assertFalse(taskRepo.upserted.last().isFinished)
            assertFalse(vm.state.value.isUncheckTaskBlockedDialogVisible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checking a task finishes every one of its subtasks`() = runTest {
        val open1 = subTask("s1")
        val done = subTask("s2", isFinished = true)
        val open2 = subTask("s3")
        val proj = project(open1, done, open2)
        val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
        val (vm, subTaskRepo) = viewModel(
            proj,
            FakeSubTaskRepository(listOf(open1, done, open2)),
            taskRepository = taskRepo
        )
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
            advanceUntilIdle()

            val task = vm.task()!!
            assertTrue(task.isFinished)
            assertTrue(task.subTasks.all { it.isFinished }, "checking the parent finishes them all")
            assertEquals(3, task.doneSubTaskCount)
            assertTrue(taskRepo.upserted.last().isFinished)
            // The already-finished one is left alone rather than re-queued for sync.
            assertEquals(
                setOf("s1", "s3"),
                subTaskRepo.upserted.map { it.projectSubTaskId }.toSet()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checking a task stops a running subtask`() = runTest {
        val s1 = subTask("s1")
        val proj = project(s1)
        val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
        val (vm, subTaskRepo) = viewModel(
            proj,
            FakeSubTaskRepository(listOf(s1)),
            taskRepository = taskRepo
        )
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s1"))
            settle()
            assertTrue(vm.task()!!.subTasks.first().isTimerRunning, "precondition: it is running")

            vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
            settle()

            assertTrue(subTaskRepo.stopped.contains("s1"))
            assertFalse(vm.task()!!.subTasks.first().isTimerRunning)
            assertTrue(vm.task()!!.subTasks.first().isFinished)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unchecking a task with subtasks is refused and raises the dialog`() = runTest {
        val done = subTask("s1", isFinished = true)
        val proj = project(done, taskFinished = true)
        val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
        val (vm, subTaskRepo) = viewModel(
            proj,
            FakeSubTaskRepository(listOf(done)),
            taskRepository = taskRepo
        )
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
            advanceUntilIdle()

            assertTrue(vm.state.value.isUncheckTaskBlockedDialogVisible)
            assertTrue(vm.task()!!.isFinished, "the refusal changes nothing")
            assertTrue(taskRepo.upserted.isEmpty())
            assertTrue(subTaskRepo.upserted.isEmpty())

            vm.onAction(ProjectDetailAction.OnDismissUncheckTaskDialog)
            advanceUntilIdle()
            assertFalse(vm.state.value.isUncheckTaskBlockedDialogVisible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unchecking a subtask un-finishes its parent task`() = runTest {
        // The escape route the uncheck-blocked dialog points the user at.
        val done = subTask("s1", isFinished = true)
        val proj = project(done, taskFinished = true)
        val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
        val (vm, _) = viewModel(
            proj,
            FakeSubTaskRepository(listOf(done)),
            taskRepository = taskRepo
        )
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnSubTaskCheckedChange("s1"))
            advanceUntilIdle()

            assertFalse(vm.task()!!.subTasks.first().isFinished)
            assertFalse(vm.task()!!.isFinished, "the parent follows its subtasks")
            assertFalse(taskRepo.upserted.last().isFinished)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checking the last open subtask finishes the parent task`() = runTest {
        val done = subTask("s1", isFinished = true)
        val open = subTask("s2")
        val proj = project(done, open)
        val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
        val (vm, _) = viewModel(
            proj,
            FakeSubTaskRepository(listOf(done, open)),
            taskRepository = taskRepo
        )
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnSubTaskCheckedChange("s2"))
            advanceUntilIdle()

            assertTrue(vm.task()!!.isFinished)
            assertTrue(taskRepo.upserted.last().isFinished)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checking a task keeps the time a running subtask just banked`() = runTest {
        val s1 = subTask("s1", durationMillis = 1_000L)
        val proj = project(s1)
        val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
        val (vm, subTaskRepo) = viewModel(
            proj,
            FakeSubTaskRepository(listOf(s1)),
            taskRepository = taskRepo
        )
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s1"))
            settle()

            vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
            settle()

            // The finishing write must be built on the post-stop row, not the snapshot taken
            // before it, or the tracked time is silently thrown away.
            val finishing = subTaskRepo.upserted.last { it.projectSubTaskId == "s1" }
            assertTrue(finishing.isFinished)
            assertEquals(1_000L + BANKED_MILLIS, finishing.durationMillis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checking a running subtask keeps the time it just banked`() = runTest {
        val s1 = subTask("s1", durationMillis = 1_000L)
        val proj = project(s1)
        val (vm, subTaskRepo) = viewModel(proj, FakeSubTaskRepository(listOf(s1)))
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s1"))
            settle()

            vm.onAction(ProjectDetailAction.OnSubTaskCheckedChange("s1"))
            settle()

            val finishing = subTaskRepo.upserted.last { it.projectSubTaskId == "s1" }
            assertTrue(finishing.isFinished)
            assertEquals(1_000L + BANKED_MILLIS, finishing.durationMillis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding a subtask un-finishes a finished parent task`() = runTest {
        // The other escape route named by the dialog.
        val done = subTask("s1", isFinished = true)
        val proj = project(done, taskFinished = true)
        val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
        val (vm, _) = viewModel(
            proj,
            FakeSubTaskRepository(listOf(done)),
            taskRepository = taskRepo
        )
        vm.state.test {
            awaitItem()
            advanceUntilIdle()

            vm.onAction(ProjectDetailAction.OnAddSubTaskClick(TASK_ID))
            // beginAddSubTask swaps in a fresh TextFieldState, and state only republishes it once
            // the scheduler runs — typing before this would fill the discarded buffer.
            advanceUntilIdle()
            vm.state.value.editSubTaskTextFieldState.setTextAndPlaceCursorAtEnd("Follow-up")
            vm.onAction(ProjectDetailAction.OnCommitSubTaskTitle)
            advanceUntilIdle()

            assertEquals(2, vm.task()!!.subTasks.size)
            assertFalse(vm.task()!!.isFinished, "a new open subtask re-opens its parent")
            assertFalse(taskRepo.upserted.last().isFinished)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The edit-text screen writes the project row through its own ViewModel while this entry sits
     * on the back stack, so the row has to reach this state on its own — there is no second load.
     */
    @Test
    fun `an edit to the project row reaches the state without a reload`() = runTest {
        val loaded = project(subTask("s1"))
        val projectRepository = FakeDetailProjectRepository(loaded)
        val (vm, _) = viewModel(loaded, projectRepository = projectRepository)
        vm.state.test {
            awaitItem()
            advanceUntilIdle()
            assertEquals("project", vm.state.value.titleText)

            projectRepository.emit(loaded.copy(title = "renamed", description = "new description"))
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals("renamed", state.titleText)
            assertEquals("new description", state.descriptionText)
            assertEquals("renamed", state.project?.title)
            // The row carries no tasks, so the loaded tree has to survive the merge untouched.
            assertEquals(1, state.project?.projectTasks?.size)
            assertEquals(1, state.project?.projectTasks?.first()?.subTasks?.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// --- fakes -------------------------------------------------------------------------------------

private class FakeDetailProjectRepository(project: Project) : ProjectRepository {
    // Held in a MutableStateFlow so a test can push an edited row the way the edit-text screen does.
    private val projectFlow = MutableStateFlow(project)

    fun emit(project: Project) { projectFlow.value = project }

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? = projectFlow.value
    override suspend fun getProjectById(projectId: String): Project? = projectFlow.value
    override fun observeProjectById(projectId: String): Flow<Project?> = projectFlow

    override fun getProjects(): Flow<List<Project>> = projectFlow.map { listOf(it) }
    override fun getActiveProjects(): Flow<List<Project>> = projectFlow.map { listOf(it) }
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

private class FakeProjectTaskRepository(
    initial: ProjectTask? = null
) : ProjectTaskRepository {
    val started = mutableListOf<String>()
    val stopped = mutableListOf<String>()
    val upserted = mutableListOf<ProjectTask>()

    private val task = MutableStateFlow(initial)

    override suspend fun startProjectTask(taskId: String): EmptyResult<DataError> {
        started += taskId
        return Result.Success(Unit)
    }

    override suspend fun stopProjectTask(taskId: String): EmptyResult<DataError> {
        stopped += taskId
        return Result.Success(Unit)
    }

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> {
        upserted += projectTask
        task.value = projectTask
        return Result.Success(Unit)
    }

    override suspend fun deleteProjectTask(projectId: String, taskId: String): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun updateProjectTaskDuration(taskId: String, newDurationMillis: Long): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun updateProjectTaskTitle(taskId: String, title: String): EmptyResult<DataError> = Result.Success(Unit)
    override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = task
    override suspend fun syncPendingTasks() = Unit
}

/** What [FakeSubTaskRepository.stopSubTask] adds to a subtask's duration, standing in for a real interval. */
private const val BANKED_MILLIS = 5_000L

private class FakeSubTaskRepository(
    initial: List<ProjectSubTask> = emptyList()
) : SubTaskRepository {
    val started = mutableListOf<String>()
    val stopped = mutableListOf<String>()
    val upserted = mutableListOf<ProjectSubTask>()
    val deleted = mutableListOf<String>()
    var lastStarted: String? = null

    private val subTasks = MutableStateFlow(initial)

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

    // Both mirror production, which writes the flag to the row as well as opening/closing the
    // interval (RoomLocalSubTaskDataSource.startTask, IntervalClosing). Callers that read the row
    // back to decide whether a timer needs stopping depend on it.
    override suspend fun startSubTask(subTaskId: String): EmptyResult<DataError> {
        started += subTaskId
        setTimerRunning(subTaskId, true)
        return Result.Success(Unit)
    }

    override suspend fun stopSubTask(subTaskId: String): EmptyResult<DataError> {
        stopped += subTaskId
        // Production banks the elapsed interval into durationMillis here. Callers that copy a
        // snapshot taken before the stop would silently write that back to zero, so the fake has
        // to reproduce the write for a test to be able to catch it.
        subTasks.value = subTasks.value.map {
            if (it.projectSubTaskId == subTaskId) {
                it.copy(isTimerRunning = false, durationMillis = (it.durationMillis ?: 0) + BANKED_MILLIS)
            } else it
        }
        return Result.Success(Unit)
    }

    private fun setTimerRunning(subTaskId: String, isRunning: Boolean) {
        subTasks.value = subTasks.value.map {
            if (it.projectSubTaskId == subTaskId) it.copy(isTimerRunning = isRunning) else it
        }
    }

    override suspend fun lastStartedSubTaskId(taskId: String): String? = lastStarted

    override suspend fun syncPendingSubTasks() = Unit
}
