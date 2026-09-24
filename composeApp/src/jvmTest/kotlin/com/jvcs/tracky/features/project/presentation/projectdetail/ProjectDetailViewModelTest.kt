@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.features.project.presentation.projectdetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.FakeRunningTimerRepository
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.runningTimer
import com.jvcs.tracky.core.domain.util.testTimeManager
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
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

    private fun subTask(
        id: String,
        durationMillis: Long = 0L,
        isFinished: Boolean = false,
    ) = ProjectSubTask(
        projectSubTaskId = id,
        parentProjectTaskId = TASK_ID,
        parentProjectId = PROJECT_ID,
        title = "subtask-$id",
        durationMillis = durationMillis,
        isTimerRunning = false,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        isFinished = isFinished,
    )

    private fun project(
        vararg subTasks: ProjectSubTask,
        taskDurationMillis: Long = 0L,
        taskFinished: Boolean = false,
    ) = Project(
        projectId = PROJECT_ID,
        title = "project",
        description = null,
        colorArgb = null,
        totalDurationMillis = null,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        isFinished = false,
        endDateTimeUtc = null,
        projectTasks =
            listOf(
                ProjectTask(
                    projectTaskId = TASK_ID,
                    title = "task",
                    description = null,
                    durationMillis = taskDurationMillis,
                    startDateTimeUtc = Instant.fromEpochMilliseconds(0),
                    parentProjectId = PROJECT_ID,
                    isTimerRunning = false,
                    isFinished = taskFinished,
                    subTasks = subTasks.toList(),
                ),
            ),
    )

    private fun TestScope.viewModel(
        project: Project,
        subTaskRepository: FakeSubTaskRepository = FakeSubTaskRepository(),
        // Seeded from the project by default: setTaskFinished reads the task row back before
        // writing, so a repository that answers null would make every finish silently no-op.
        taskRepository: FakeProjectTaskRepository =
            FakeProjectTaskRepository(project.projectTasks?.firstOrNull()),
        projectRepository: FakeDetailProjectRepository = FakeDetailProjectRepository(project),
        // The open interval is what says a timer is running, so the fakes that open one publish it
        // here and TimeManager renders it - the same path production takes.
        running: FakeRunningTimerRepository = FakeRunningTimerRepository(),
    ): Pair<ProjectDetailViewModel, FakeSubTaskRepository> {
        subTaskRepository.running = running
        taskRepository.running = running
        val vm =
            ProjectDetailViewModel(
                projectId = PROJECT_ID,
                projectRepository = projectRepository,
                projectTaskRepository = taskRepository,
                subTaskRepository = subTaskRepository,
                timeManager = testTimeManager(repository = running),
                timeProvider = FakeTimeProvider(),
                ioDispatcher = dispatcher,
            )
        return vm to subTaskRepository
    }

    private fun ProjectDetailViewModel.task() =
        state.value.project
            ?.projectTasks
            ?.first()

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

    /** A project whose task list is what a reorder acts on, one task per id. */
    private fun projectWithTasks(vararg taskIds: String) =
        Project(
            projectId = PROJECT_ID,
            title = "project",
            description = null,
            colorArgb = null,
            totalDurationMillis = null,
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            isFinished = false,
            endDateTimeUtc = null,
            projectTasks =
                taskIds.map { id ->
                    ProjectTask(
                        projectTaskId = id,
                        title = "task-$id",
                        description = null,
                        durationMillis = 0L,
                        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
                        parentProjectId = PROJECT_ID,
                        isTimerRunning = false,
                        subTasks = emptyList(),
                    )
                },
        )

    private fun ProjectDetailViewModel.taskIds() =
        state.value.project
            ?.projectTasks
            ?.map { it.projectTaskId }

    private fun ProjectDetailViewModel.subTaskIds() =
        state.value.project
            ?.projectTasks
            ?.first()
            ?.subTasks
            ?.map { it.projectSubTaskId }

    // --- tests ---------------------------------------------------------------------------------

    @Test
    fun `starting a subtask marks the parent task running`() =
        runTest {
            val (vm, _) = viewModel(project(subTask("s1")))
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s1"))
                settle()

                val task = vm.task()!!
                assertThat(task.isTimerRunning, name = "parent should run while a subtask runs").isTrue()
                assertThat(task.subTasks.first { it.projectSubTaskId == "s1" }.isTimerRunning).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `parent duration is the sum of its subtasks, not its own banked time`() =
        runTest {
            // Own time deliberately differs from the sum so we can tell which one is displayed.
            val (vm, _) =
                viewModel(
                    project(
                        subTask("s1", durationMillis = 60_000),
                        subTask("s2", durationMillis = 30_000),
                        taskDurationMillis = 999_000,
                    ),
                )
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                assertThat(vm.task()!!.displayDuration).isEqualTo("00:01:30")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a task without subtasks still shows its own duration`() =
        runTest {
            val (vm, _) = viewModel(project(taskDurationMillis = 60_000))
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                val task = vm.task()!!
                assertThat(task.subTasks.isEmpty()).isTrue()
                assertThat(task.displayDuration).isEqualTo(task.formattedDuration)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `starting a sibling stops the one already running`() =
        runTest {
            val (vm, _) = viewModel(project(subTask("s1"), subTask("s2")))
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s1"))
                settle()
                vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s2"))
                settle()

                val subTasks = vm.task()!!.subTasks
                assertThat(
                    subTasks
                        .first {
                            it.projectSubTaskId == "s1"
                        }.isTimerRunning,
                    name = "the first subtask must stop in TimeManager too, not only in the database",
                ).isFalse()
                assertThat(subTasks.first { it.projectSubTaskId == "s2" }.isTimerRunning).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `only the running subtask accrues time, so the parent sum does not double count`() =
        runTest {
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
                assertThat(subTasks.count { it.isTimerRunning }).isEqualTo(1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `parent button stops whichever subtask is running`() =
        runTest {
            val (vm, repo) = viewModel(project(subTask("s1")))
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s1"))
                settle()
                vm.onAction(ProjectDetailAction.OnToggleSessionTimer(TASK_ID))
                settle()

                assertThat(vm.task()!!.isTimerRunning).isFalse()
                assertThat(repo.stopped).isEqualTo(listOf("s1"))
                // The parent's own timer must never be touched directly.
                assertThat(repo.started.isNotEmpty()).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `parent button resumes the last started subtask`() =
        runTest {
            val repo = FakeSubTaskRepository().apply { lastStarted = "s2" }
            val (vm, _) = viewModel(project(subTask("s1"), subTask("s2")), repo)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnToggleSessionTimer(TASK_ID))
                settle()

                assertThat(repo.started).isEqualTo(listOf("s2"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `parent button falls back to the first unfinished subtask`() =
        runTest {
            val repo = FakeSubTaskRepository() // nothing has ever run
            val (vm, _) = viewModel(project(subTask("s1", isFinished = true), subTask("s2")), repo)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnToggleSessionTimer(TASK_ID))
                settle()

                assertThat(repo.started, name = "a finished subtask must not be resumed").isEqualTo(listOf("s2"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `parent button does nothing when every subtask is finished`() =
        runTest {
            val repo = FakeSubTaskRepository()
            val (vm, _) = viewModel(project(subTask("s1", isFinished = true)), repo)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnToggleSessionTimer(TASK_ID))
                advanceUntilIdle()

                assertThat(repo.started.isEmpty()).isTrue()
                assertThat(vm.task()!!.isTimerRunning).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `checking a subtask marks it finished in state without a reload`() =
        runTest {
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
                assertThat(task.subTasks.first { it.projectSubTaskId == "s1" }.isFinished).isTrue()
                assertThat(task.doneSubTaskCount, name = "the progress row reads off state too").isEqualTo(1)
                assertThat(repo.upserted.last().isFinished).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unchecking a finished subtask clears it in state`() =
        runTest {
            val s1 = subTask("s1", isFinished = true)
            val (vm, repo) = viewModel(project(s1), FakeSubTaskRepository(listOf(s1)))
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnSubTaskCheckedChange("s1"))
                advanceUntilIdle()

                assertThat(
                    vm
                        .task()!!
                        .subTasks
                        .first()
                        .isFinished,
                ).isFalse()
                assertThat(repo.upserted.last().isFinished).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- task checkbox ---------------------------------------------------------------------

    @Test
    fun `checking a task without subtasks finishes it`() =
        runTest {
            val taskRepo = FakeProjectTaskRepository(project().projectTasks!!.first())
            val (vm, _) = viewModel(project(), taskRepository = taskRepo)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
                advanceUntilIdle()

                assertThat(vm.task()!!.isFinished).isTrue()
                assertThat(taskRepo.upserted.last().isFinished).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `checking a running task without subtasks stops its timer`() =
        runTest {
            val taskRepo = FakeProjectTaskRepository(project().projectTasks!!.first())
            val (vm, _) = viewModel(project(), taskRepository = taskRepo)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnToggleSessionTimer(TASK_ID))
                settle()
                assertThat(vm.task()!!.isTimerRunning, name = "precondition: the timer is running").isTrue()

                vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
                settle()

                assertThat(taskRepo.stopped.contains(TASK_ID), name = "a finished task must not keep counting").isTrue()
                assertThat(vm.task()!!.isTimerRunning).isFalse()
                assertThat(vm.task()!!.isFinished).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unchecking a task without subtasks clears it`() =
        runTest {
            val finished = project(taskFinished = true)
            val taskRepo = FakeProjectTaskRepository(finished.projectTasks!!.first())
            val (vm, _) = viewModel(finished, taskRepository = taskRepo)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
                advanceUntilIdle()

                assertThat(vm.task()!!.isFinished, name = "a task with no subtasks toggles freely").isFalse()
                assertThat(taskRepo.upserted.last().isFinished).isFalse()
                assertThat(vm.state.value.isUncheckTaskBlockedDialogVisible).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `checking a task finishes every one of its subtasks`() =
        runTest {
            val open1 = subTask("s1")
            val done = subTask("s2", isFinished = true)
            val open2 = subTask("s3")
            val proj = project(open1, done, open2)
            val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
            val (vm, subTaskRepo) =
                viewModel(
                    proj,
                    FakeSubTaskRepository(listOf(open1, done, open2)),
                    taskRepository = taskRepo,
                )
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
                advanceUntilIdle()

                val task = vm.task()!!
                assertThat(task.isFinished).isTrue()
                assertThat(task.subTasks.all { it.isFinished }, name = "checking the parent finishes them all").isTrue()
                assertThat(task.doneSubTaskCount).isEqualTo(3)
                assertThat(taskRepo.upserted.last().isFinished).isTrue()
                // The already-finished one is left alone rather than re-queued for sync.
                assertThat(subTaskRepo.upserted.map { it.projectSubTaskId }.toSet()).isEqualTo(setOf("s1", "s3"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `checking a task stops a running subtask`() =
        runTest {
            val s1 = subTask("s1")
            val proj = project(s1)
            val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
            val (vm, subTaskRepo) =
                viewModel(
                    proj,
                    FakeSubTaskRepository(listOf(s1)),
                    taskRepository = taskRepo,
                )
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnToggleSubTaskTimer("s1"))
                settle()
                assertThat(
                    vm
                        .task()!!
                        .subTasks
                        .first()
                        .isTimerRunning,
                    name = "precondition: it is running",
                ).isTrue()

                vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
                settle()

                assertThat(subTaskRepo.stopped.contains("s1")).isTrue()
                assertThat(
                    vm
                        .task()!!
                        .subTasks
                        .first()
                        .isTimerRunning,
                ).isFalse()
                assertThat(
                    vm
                        .task()!!
                        .subTasks
                        .first()
                        .isFinished,
                ).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unchecking a task with subtasks is refused and raises the dialog`() =
        runTest {
            val done = subTask("s1", isFinished = true)
            val proj = project(done, taskFinished = true)
            val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
            val (vm, subTaskRepo) =
                viewModel(
                    proj,
                    FakeSubTaskRepository(listOf(done)),
                    taskRepository = taskRepo,
                )
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnTaskCheckedChange(TASK_ID))
                advanceUntilIdle()

                assertThat(vm.state.value.isUncheckTaskBlockedDialogVisible).isTrue()
                assertThat(vm.task()!!.isFinished, name = "the refusal changes nothing").isTrue()
                assertThat(taskRepo.upserted.isEmpty()).isTrue()
                assertThat(subTaskRepo.upserted.isEmpty()).isTrue()

                vm.onAction(ProjectDetailAction.OnDismissUncheckTaskDialog)
                advanceUntilIdle()
                assertThat(vm.state.value.isUncheckTaskBlockedDialogVisible).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unchecking a subtask un-finishes its parent task`() =
        runTest {
            // The escape route the uncheck-blocked dialog points the user at.
            val done = subTask("s1", isFinished = true)
            val proj = project(done, taskFinished = true)
            val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
            val (vm, _) =
                viewModel(
                    proj,
                    FakeSubTaskRepository(listOf(done)),
                    taskRepository = taskRepo,
                )
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnSubTaskCheckedChange("s1"))
                advanceUntilIdle()

                assertThat(
                    vm
                        .task()!!
                        .subTasks
                        .first()
                        .isFinished,
                ).isFalse()
                assertThat(vm.task()!!.isFinished, name = "the parent follows its subtasks").isFalse()
                assertThat(taskRepo.upserted.last().isFinished).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `checking the last open subtask finishes the parent task`() =
        runTest {
            val done = subTask("s1", isFinished = true)
            val open = subTask("s2")
            val proj = project(done, open)
            val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
            val (vm, _) =
                viewModel(
                    proj,
                    FakeSubTaskRepository(listOf(done, open)),
                    taskRepository = taskRepo,
                )
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnSubTaskCheckedChange("s2"))
                advanceUntilIdle()

                assertThat(vm.task()!!.isFinished).isTrue()
                assertThat(taskRepo.upserted.last().isFinished).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `checking a task keeps the time a running subtask just banked`() =
        runTest {
            val s1 = subTask("s1", durationMillis = 1_000L)
            val proj = project(s1)
            val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
            val (vm, subTaskRepo) =
                viewModel(
                    proj,
                    FakeSubTaskRepository(listOf(s1)),
                    taskRepository = taskRepo,
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
                assertThat(finishing.isFinished).isTrue()
                assertThat(finishing.durationMillis).isEqualTo(1_000L + BANKED_MILLIS)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `checking a running subtask keeps the time it just banked`() =
        runTest {
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
                assertThat(finishing.isFinished).isTrue()
                assertThat(finishing.durationMillis).isEqualTo(1_000L + BANKED_MILLIS)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a subtask created elsewhere un-finishes a finished parent on return`() =
        runTest {
            // The other escape route named by the dialog, now taken on the edit-text screen.
            val done = subTask("s1", isFinished = true)
            val proj = project(done, taskFinished = true)
            val taskRepo = FakeProjectTaskRepository(proj.projectTasks!!.first())
            val projectRepo = FakeDetailProjectRepository(proj)
            val (vm, _) =
                viewModel(
                    proj,
                    FakeSubTaskRepository(listOf(done)),
                    taskRepository = taskRepo,
                    projectRepository = projectRepo,
                )
            vm.state.test {
                awaitItem()
                advanceUntilIdle()
                projectRepo.emit(project(done, subTask("s2"), taskFinished = true))
                advanceUntilIdle()

                assertThat(vm.task()!!.subTasks.size).isEqualTo(2)
                assertThat(vm.task()!!.isFinished, name = "a new open subtask re-opens its parent").isFalse()
                assertThat(taskRepo.upserted.last().isFinished).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an edited task arrives on its own but keeps an unsaved colour`() =
        runTest {
            val proj = project(subTask("s1"))
            val projectRepo = FakeDetailProjectRepository(proj)
            val (vm, _) = viewModel(proj, projectRepository = projectRepo)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()
                vm.onAction(ProjectDetailAction.OnEditModeClick)
                vm.onAction(ProjectDetailAction.OnColorChanged(Color.Red))
                advanceUntilIdle()

                val edited = project(subTask("s1").copy(title = "renamed subtask"))
                projectRepo.emit(
                    edited.copy(projectTasks = edited.projectTasks!!.map { it.copy(title = "renamed task") }),
                )
                advanceUntilIdle()

                assertThat(vm.task()!!.title).isEqualTo("renamed task")
                assertThat(
                    vm
                        .task()!!
                        .subTasks
                        .single()
                        .title,
                ).isEqualTo("renamed subtask")
                assertThat(vm.state.value.projectColor, name = "an unsaved colour pick survives").isEqualTo(Color.Red)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** The chain from the open interval's provenance all the way to the card. */
    @Test
    fun `a timer another device started is marked foreign on screen`() =
        runTest {
            val running = FakeRunningTimerRepository()
            val (vm, _) = viewModel(project(taskDurationMillis = 0), running = running)

            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                running.startTimer(runningTimer(taskId = TASK_ID, isForeign = true))
                settle()

                assertThat(vm.state.value.isRunningTimerForeign, name = "the hero card has nothing to say").isTrue()
                assertThat(vm.task()!!.isForeign).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a timer this device started is not marked foreign`() =
        runTest {
            val running = FakeRunningTimerRepository()
            val (vm, _) = viewModel(project(taskDurationMillis = 0), running = running)

            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                running.startTimer(runningTimer(taskId = TASK_ID))
                settle()

                assertThat(vm.state.value.isRunningTimerForeign).isFalse()
                assertThat(vm.task()!!.isForeign).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** Replaces a test that asserted the opposite while the tree was read once and refreshed by hand. */
    @Test
    fun `a subtask added elsewhere arrives without the screen being returned to`() =
        runTest {
            val proj = project(subTask("s1"))
            val projectRepo = FakeDetailProjectRepository(proj)
            val (vm, _) = viewModel(proj, projectRepository = projectRepo)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                projectRepo.emit(project(subTask("s1"), subTask("s2")))
                advanceUntilIdle()

                assertThat(vm.subTaskIds()).isEqualTo(listOf("s1", "s2"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The edit-text screen writes the project row through its own ViewModel while this entry sits
     * on the back stack, so the row has to reach this state on its own — there is no second load.
     */
    @Test
    fun `an edit to the project row reaches the state without a reload`() =
        runTest {
            val loaded = project(subTask("s1"))
            val projectRepository = FakeDetailProjectRepository(loaded)
            val (vm, _) = viewModel(loaded, projectRepository = projectRepository)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()
                assertThat(vm.state.value.titleText).isEqualTo("project")

                projectRepository.emit(loaded.copy(title = "renamed", description = "new description"))
                advanceUntilIdle()

                val state = vm.state.value
                assertThat(state.titleText).isEqualTo("renamed")
                assertThat(state.descriptionText).isEqualTo("new description")
                assertThat(state.project?.title).isEqualTo("renamed")
                // The row carries no tasks, so the loaded tree has to survive the merge untouched.
                assertThat(state.project?.projectTasks?.size).isEqualTo(1)
                assertThat(
                    state.project
                        ?.projectTasks
                        ?.first()
                        ?.subTasks
                        ?.size,
                ).isEqualTo(1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- reorder -------------------------------------------------------------------------------

    @Test
    fun `a move reorders the shown tasks without persisting anything`() =
        runTest {
            // Crossing a neighbour fires repeatedly during a drag, so it must stay in memory — the
            // write happens once, on drop.
            val taskRepository = FakeProjectTaskRepository()
            val (vm, _) = viewModel(projectWithTasks("a", "b", "c"), taskRepository = taskRepository)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnTaskReorderMove(fromTaskId = "c", toTaskId = "a"))
                settle()

                assertThat(vm.taskIds()).isEqualTo(listOf("c", "a", "b"))
                assertThat(
                    taskRepository.reorderCalls.isEmpty(),
                    name = "a move must not reach the repository",
                ).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `several moves during one drag leave only the settled order`() =
        runTest {
            val (vm, _) = viewModel(projectWithTasks("a", "b", "c"))
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnTaskReorderMove(fromTaskId = "a", toTaskId = "b"))
                vm.onAction(ProjectDetailAction.OnTaskReorderMove(fromTaskId = "a", toTaskId = "c"))
                settle()

                assertThat(vm.taskIds()).isEqualTo(listOf("b", "c", "a"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a move naming a task that is not in the list is ignored`() =
        runTest {
            // The drag state hit-tests against what is on screen, which can lag a delete.
            val (vm, _) = viewModel(projectWithTasks("a", "b"))
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnTaskReorderMove(fromTaskId = "ghost", toTaskId = "a"))
                settle()

                assertThat(vm.taskIds()).isEqualTo(listOf("a", "b"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `commit persists the settled order exactly once`() =
        runTest {
            val taskRepository = FakeProjectTaskRepository()
            val (vm, _) = viewModel(projectWithTasks("a", "b", "c"), taskRepository = taskRepository)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnTaskReorderMove(fromTaskId = "c", toTaskId = "a"))
                vm.onAction(ProjectDetailAction.OnTaskReorderCommit)
                settle()

                assertThat(taskRepository.reorderCalls).isEqualTo(listOf(listOf("c", "a", "b")))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving after a task reorder leaves the project row alone`() =
        runTest {
            // Edit mode is where tasks are dragged, so Save follows every reorder. Rewriting the project
            // then used to null its sortIndex and move it to the top of the overview's Custom order.
            val projectRepository = FakeDetailProjectRepository(projectWithTasks("a", "b").copy(sortIndex = 3))
            val (vm, _) = viewModel(projectWithTasks("a", "b"), projectRepository = projectRepository)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnEditModeClick)
                vm.onAction(ProjectDetailAction.OnTaskReorderMove(fromTaskId = "b", toTaskId = "a"))
                vm.onAction(ProjectDetailAction.OnTaskReorderCommit)
                settle()
                vm.onAction(ProjectDetailAction.OnSaveClick)
                settle()

                assertThat(projectRepository.upserted.isEmpty(), name = "nothing on the project changed").isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving a colour change keeps the project's place in the manual order`() =
        runTest {
            val stored =
                projectWithTasks("a").copy(
                    sortIndex = 3,
                    isPinned = true,
                    startDateTimeUtc = Instant.parse("2026-09-18T10:12:03.145Z"),
                )
            val projectRepository = FakeDetailProjectRepository(stored)
            val (vm, _) = viewModel(stored, projectRepository = projectRepository)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnEditModeClick)
                vm.onAction(ProjectDetailAction.OnColorChanged(Color.Red))
                // Save reads the combined state, which a tap only reaches once it has been folded in.
                settle()
                vm.onAction(ProjectDetailAction.OnSaveClick)
                settle()

                val saved = projectRepository.upserted.single()
                assertThat(saved.colorArgb).isEqualTo(Color.Red.toArgb())
                assertThat(saved.sortIndex).isEqualTo(3L)
                assertThat(saved.isPinned).isTrue()
                assertThat(saved.startDateTimeUtc, name = "not truncated to the day").isEqualTo(stored.startDateTimeUtc)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed commit rolls the list back and reports it`() =
        runTest {
            val taskRepository = FakeProjectTaskRepository()
            taskRepository.reorderFailWith = DataError.Local.DISK_FULL
            val (vm, _) = viewModel(projectWithTasks("a", "b", "c"), taskRepository = taskRepository)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.events.test {
                    vm.onAction(ProjectDetailAction.OnTaskReorderMove(fromTaskId = "c", toTaskId = "a"))
                    vm.onAction(ProjectDetailAction.OnTaskReorderCommit)
                    settle()

                    assertThat(awaitItem() is ProjectDetailEvent.ReorderError).isTrue()
                    cancelAndIgnoreRemainingEvents()
                }
                // Don't leave the user looking at an order that says it saved while the snackbar says
                // it did not: the list goes back to what is actually persisted.
                assertThat(vm.taskIds()).isEqualTo(listOf("a", "b", "c"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cancelling a drag discards the preview order`() =
        runTest {
            val taskRepository = FakeProjectTaskRepository()
            val (vm, _) = viewModel(projectWithTasks("a", "b", "c"), taskRepository = taskRepository)
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnTaskReorderMove(fromTaskId = "c", toTaskId = "a"))
                vm.onAction(ProjectDetailAction.OnTaskReorderCancel)
                settle()

                assertThat(vm.taskIds()).isEqualTo(listOf("a", "b", "c"))
                assertThat(taskRepository.reorderCalls.isEmpty(), name = "an aborted drag saves nothing").isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a subtask move reorders that card without persisting anything`() =
        runTest {
            val (vm, subTaskRepository) = viewModel(project(subTask("s1"), subTask("s2"), subTask("s3")))
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(
                    ProjectDetailAction.OnSubTaskReorderMove(
                        taskId = TASK_ID,
                        fromSubTaskId = "s3",
                        toSubTaskId = "s1",
                    ),
                )
                settle()

                assertThat(vm.subTaskIds()).isEqualTo(listOf("s3", "s1", "s2"))
                assertThat(
                    subTaskRepository.reorderCalls.isEmpty(),
                    name = "a move must not reach the repository",
                ).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `committing a subtask reorder persists the settled order once, under its own task`() =
        runTest {
            val (vm, subTaskRepository) = viewModel(project(subTask("s1"), subTask("s2")))
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(
                    ProjectDetailAction.OnSubTaskReorderMove(
                        taskId = TASK_ID,
                        fromSubTaskId = "s2",
                        toSubTaskId = "s1",
                    ),
                )
                vm.onAction(ProjectDetailAction.OnSubTaskReorderCommit(TASK_ID))
                settle()

                assertThat(subTaskRepository.reorderCalls).isEqualTo(listOf(TASK_ID to listOf("s2", "s1")))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a subtask move aimed at another task is ignored`() =
        runTest {
            // A subtask never leaves its parent, so a move naming a task this card does not own must
            // not rewrite anything.
            val (vm, _) = viewModel(project(subTask("s1"), subTask("s2")))
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(
                    ProjectDetailAction.OnSubTaskReorderMove(
                        taskId = "some-other-task",
                        fromSubTaskId = "s2",
                        toSubTaskId = "s1",
                    ),
                )
                settle()

                assertThat(vm.subTaskIds()).isEqualTo(listOf("s1", "s2"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed subtask commit rolls the card back and reports it`() =
        runTest {
            val subTaskRepository = FakeSubTaskRepository()
            subTaskRepository.reorderFailWith = DataError.Local.DISK_FULL
            val (vm, _) =
                viewModel(
                    project(subTask("s1"), subTask("s2")),
                    subTaskRepository = subTaskRepository,
                )
            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.events.test {
                    vm.onAction(
                        ProjectDetailAction.OnSubTaskReorderMove(
                            taskId = TASK_ID,
                            fromSubTaskId = "s2",
                            toSubTaskId = "s1",
                        ),
                    )
                    vm.onAction(ProjectDetailAction.OnSubTaskReorderCommit(TASK_ID))
                    settle()

                    assertThat(awaitItem() is ProjectDetailEvent.ReorderError).isTrue()
                    cancelAndIgnoreRemainingEvents()
                }
                assertThat(vm.subTaskIds()).isEqualTo(listOf("s1", "s2"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- the task tree is streamed, not snapshotted ------------------------------------------

    /**
     * The regression these cover. The tree used to be read once, on entry, and refreshed only when
     * the screen was returned to, so a sync that wrote another device's rows into Room left an
     * open detail screen showing figures from whenever the user last opened it.
     */
    @Test
    fun adoptsATaskTreeWrittenUnderneathIt() =
        runTest {
            val repository = FakeDetailProjectRepository(project(taskDurationMillis = 1_000))
            val (vm, _) = viewModel(project(taskDurationMillis = 1_000), projectRepository = repository)

            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                // What a pull of another device's stop looks like from here: a new banked duration.
                repository.emit(project(taskDurationMillis = 9_000))
                settle()

                assertThat(vm.task()?.durationMillis).isEqualTo(9_000L)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun picksUpATaskCreatedOnAnotherDevice() =
        runTest {
            val repository = FakeDetailProjectRepository(projectWithTasks("t1"))
            val (vm, _) = viewModel(projectWithTasks("t1"), projectRepository = repository)

            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                repository.emit(projectWithTasks("t1", "t2"))
                settle()

                assertThat(
                    vm.state.value.project
                        ?.projectTasks
                        ?.map { it.projectTaskId },
                ).isEqualTo(listOf("t1", "t2"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The tree carries the banked figure; the ticker carries the live one. A tree emission must
     * not snap a running timer back to what the row last stored.
     */
    @Test
    fun aTreeEmissionDoesNotClobberARunningTimer() =
        runTest {
            val running = FakeRunningTimerRepository()
            val repository = FakeDetailProjectRepository(project(taskDurationMillis = 0))
            val (vm, _) =
                viewModel(
                    project(taskDurationMillis = 0),
                    projectRepository = repository,
                    running = running,
                )

            vm.state.test {
                awaitItem()
                advanceUntilIdle()
                // Banked, so the live figure is non-zero even against a pinned clock.
                running.startTimer(runningTimer(taskId = TASK_ID, bankedDuration = 5.seconds))
                settle()
                val whileRunning = vm.task()?.durationMillis ?: 0L
                assertThat(whileRunning, name = "the ticker should be driving the duration").isEqualTo(5_000L)

                // A distinct value, or the StateFlow behind the fake conflates it away and the
                // collector never runs -- which would make this assertion pass without the guard.
                repository.emit(project(taskDurationMillis = 0).copy(description = "touched"))
                settle()

                assertThat(
                    (vm.task()?.durationMillis ?: 0L) >= whileRunning,
                    name = "a tree emission reset the live duration to the stored one",
                ).isTrue()
                assertThat(vm.task()?.isTimerRunning).isEqualTo(true)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** A drag is the one time the screen's order outranks the database's. */
    @Test
    fun aTreeEmissionMidDragDoesNotReplayTheMove() =
        runTest {
            val repository = FakeDetailProjectRepository(projectWithTasks("t1", "t2", "t3"))
            val (vm, _) =
                viewModel(
                    projectWithTasks("t1", "t2", "t3"),
                    projectRepository = repository,
                )

            vm.state.test {
                awaitItem()
                advanceUntilIdle()

                vm.onAction(ProjectDetailAction.OnTaskReorderMove("t3", "t1"))
                runCurrent()
                // Distinct from the held value, so the flow actually emits (see above).
                repository.emit(projectWithTasks("t1", "t2", "t3").copy(description = "touched"))
                settle()

                assertThat(
                    vm.state.value.project
                        ?.projectTasks
                        ?.map { it.projectTaskId },
                    name = "the database order overwrote the drag the user is still holding",
                ).isEqualTo(listOf("t3", "t1", "t2"))
                cancelAndIgnoreRemainingEvents()
            }
        }
}

// --- fakes -------------------------------------------------------------------------------------

private class FakeDetailProjectRepository(project: Project) : ProjectRepository {
    // Held in a MutableStateFlow so a test can push an edited row the way the edit-text screen does.
    private val projectFlow = MutableStateFlow(project)

    fun emit(project: Project) {
        projectFlow.value = project
    }

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? = projectFlow.value

    override suspend fun getProjectById(projectId: String): Project? = projectFlow.value

    override fun observeProjectById(projectId: String): Flow<Project?> = projectFlow

    // Room would repaint both from the same write, so emit() drives the tree too.
    override fun observeProjectWithTaskTreeById(projectId: String): Flow<Project?> = projectFlow

    override fun getProjects(): Flow<List<Project>> = projectFlow.map { listOf(it) }

    override fun getActiveProjects(): Flow<List<Project>> = projectFlow.map { listOf(it) }

    override suspend fun fetchProjects(): EmptyResult<DataError> = Result.Success(Unit)

    val upserted = mutableListOf<Project>()

    override suspend fun upsertProject(project: Project): EmptyResult<DataError> {
        upserted += project
        return Result.Success(Unit)
    }

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun deleteAllProjects() = Unit

    override suspend fun syncPendingProjects() = Unit
}

private class FakeProjectTaskRepository(initial: ProjectTask? = null) : ProjectTaskRepository {
    var running = FakeRunningTimerRepository()
    val started = mutableListOf<String>()
    val stopped = mutableListOf<String>()
    val upserted = mutableListOf<ProjectTask>()

    private val task = MutableStateFlow(initial)

    override suspend fun startProjectTask(taskId: String): EmptyResult<DataError> {
        started += taskId
        running.startTimer(runningTimer(taskId = taskId))
        return Result.Success(Unit)
    }

    override suspend fun stopProjectTask(taskId: String): EmptyResult<DataError> {
        stopped += taskId
        running.stopTimer()
        return Result.Success(Unit)
    }

    /** The settled order handed to each reorder call, so a test can assert it ran exactly once. */
    val reorderCalls = mutableListOf<List<String>>()
    var reorderFailWith: DataError? = null

    override suspend fun reorderTasks(projectId: String, orderedTaskIds: List<String>): EmptyResult<DataError> {
        reorderCalls += orderedTaskIds
        return reorderFailWith?.let { Result.Error(it) } ?: Result.Success(Unit)
    }

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> {
        upserted += projectTask
        task.value = projectTask
        return Result.Success(Unit)
    }

    override suspend fun deleteProjectTask(projectId: String, taskId: String): EmptyResult<DataError> =
        Result.Success(Unit)

    override suspend fun updateProjectTaskDuration(taskId: String, newDurationMillis: Long): EmptyResult<DataError> =
        Result.Success(Unit)

    override suspend fun updateProjectTaskTitle(taskId: String, title: String): EmptyResult<DataError> =
        Result.Success(Unit)

    override suspend fun updateProjectTaskText(
        taskId: String,
        title: String,
        description: String?,
    ) = Result.Success(Unit)

    override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = task

    override suspend fun syncPendingTasks() = Unit
}

/** What [FakeSubTaskRepository.stopSubTask] adds to a subtask's duration, standing in for a real interval. */
private const val BANKED_MILLIS = 5_000L

private class FakeSubTaskRepository(initial: List<ProjectSubTask> = emptyList()) : SubTaskRepository {
    var running = FakeRunningTimerRepository()
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
        } +
            subTask
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
        // Only one timer runs, so publishing this one is also what stops a running sibling.
        running.startTimer(runningTimer(taskId = TASK_ID, subTaskId = subTaskId))
        return Result.Success(Unit)
    }

    override suspend fun stopSubTask(subTaskId: String): EmptyResult<DataError> {
        stopped += subTaskId
        running.stopTimer()
        // Production banks the elapsed interval into durationMillis here. Callers that copy a
        // snapshot taken before the stop would silently write that back to zero, so the fake has
        // to reproduce the write for a test to be able to catch it.
        subTasks.value =
            subTasks.value.map {
                if (it.projectSubTaskId == subTaskId) {
                    it.copy(isTimerRunning = false, durationMillis = (it.durationMillis ?: 0) + BANKED_MILLIS)
                } else {
                    it
                }
            }
        return Result.Success(Unit)
    }

    private fun setTimerRunning(subTaskId: String, isRunning: Boolean) {
        subTasks.value =
            subTasks.value.map {
                if (it.projectSubTaskId == subTaskId) it.copy(isTimerRunning = isRunning) else it
            }
    }

    override suspend fun lastStartedSubTaskId(taskId: String): String? = lastStarted

    /** The settled order handed to each reorder call, so a test can assert it ran exactly once. */
    val reorderCalls = mutableListOf<Pair<String, List<String>>>()
    var reorderFailWith: DataError? = null

    override suspend fun reorderSubTasks(taskId: String, orderedSubTaskIds: List<String>): EmptyResult<DataError> {
        reorderCalls += taskId to orderedSubTaskIds
        return reorderFailWith?.let { Result.Error(it) } ?: Result.Success(Unit)
    }

    override suspend fun syncPendingSubTasks() = Unit
}
