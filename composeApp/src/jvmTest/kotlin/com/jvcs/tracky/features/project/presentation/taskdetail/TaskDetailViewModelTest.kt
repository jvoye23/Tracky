package com.jvcs.tracky.features.project.presentation.taskdetail

import androidx.compose.ui.graphics.toArgb
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.FakeRunningTimerRepository
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.runningTimer
import com.jvcs.tracky.core.domain.util.testTimeManager
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.presentation.fakes.FakeProjectRepository
import com.jvcs.tracky.features.project.presentation.fakes.interval
import com.jvcs.tracky.features.project.presentation.fakes.project
import com.jvcs.tracky.features.project.presentation.fakes.subInterval
import com.jvcs.tracky.features.project.presentation.fakes.subTask
import com.jvcs.tracky.features.project.presentation.fakes.task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Task Detail's "Daily sessions" list.
 *
 * It used to fold the task's own intervals directly, which made it the one screen that ignored the
 * subtask-nesting rule and billed a multi-day interval entirely to its start day — the two reasons
 * a row here once read `75:21:06:12`. It now goes through `countedDayIntervals` like every other
 * per-day view.
 *
 * The zone is the system's (the ViewModel reads it), so every fixture keeps both intervals at the
 * same instant and asserts on the totals rather than on a particular date.
 */
internal class TaskDetailViewModelTest {

    private val stored = MutableStateFlow<ProjectTask?>(null)
    private val storedSubTasks = MutableStateFlow<List<ProjectSubTask>>(emptyList())
    private val running = FakeRunningTimerRepository()
    private val taskRepository = FakeProjectTaskRepository()
    private val subTaskRepository = FakeSubTaskRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModelFor(task: ProjectTask, project: Project? = null): TaskDetailViewModel {
        // Subtasks reach the ViewModel through their own repository, as in production, where the
        // task row carries none.
        stored.value = task.copy(subTasks = null)
        storedSubTasks.value = task.subTasks.orEmpty()
        val viewModel =
            TaskDetailViewModel(
                taskId = task.projectTaskId,
                projectTaskRepository = taskRepository,
                subTaskRepository = subTaskRepository,
                projectRepository = FakeProjectRepository(project),
                timeManager = testTimeManager(repository = running),
            )
        // state is a WhileSubscribed stateIn, so loadSession does not run until something collects.
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun aDayTotalIsFormattedWithoutCentiseconds() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel =
                viewModelFor(
                    task(intervals = listOf(interval("2026-09-09T12:00:00Z", minutes = 30))),
                )

            // Three segments, not formatDuration's four: a day total is not a stopwatch reading, and
            // the unbounded-hours dialect is what let an impossible number render.
            assertThat(
                viewModel.state.value.dailyStatistics
                    .single()
                    .formattedDuration,
            ).isEqualTo("00:30:00")
        }

    @Test
    fun aTaskWithSubTasksIsCountedThroughThemOnly() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel =
                viewModelFor(
                    task(
                        // The enclosing task interval a subtask timer opens. Counting it as well would
                        // double-bill the same stretch of wall clock.
                        intervals = listOf(interval("2026-09-09T12:00:00Z", minutes = 60)),
                        subTasks =
                            listOf(
                                subTask(intervals = listOf(subInterval("2026-09-09T12:00:00Z", minutes = 30))),
                            ),
                    ),
                )

            val stats = viewModel.state.value.dailyStatistics
            assertThat(stats.size).isEqualTo(1)
            assertThat(stats.single().formattedDuration).isEqualTo("00:30:00")
        }

    @Test
    fun anIntervalRunningPastMidnightIsSplitAcrossTheTwoDays() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel =
                viewModelFor(
                    // Noon-to-noon: two calendar days in every zone, and exactly 24h banked.
                    task(intervals = listOf(interval("2026-09-09T12:00:00Z", minutes = 24 * 60))),
                )

            val stats = viewModel.state.value.dailyStatistics
            assertThat(stats.size, name = "one day cannot hold a 24-hour stretch that started at noon").isEqualTo(2)
            // Still 24 hours in total, just no longer all on one row.
            assertThat(
                stats.sumOf { stat ->
                    stat.formattedDuration.split(":").let { (h, m, s) ->
                        h.toLong() * 3600 + m.toLong() * 60 + s.toLong()
                    }
                },
            ).isEqualTo(24 * 60 * 60L)
        }

    @Test
    fun anOpenIntervalContributesNothing() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel =
                viewModelFor(
                    task(intervals = listOf(interval("2026-09-09T12:00:00Z", minutes = 30, open = true))),
                )

            // A running timer banks nothing until it stops - and a stranded one banks nothing at all
            // until it is reviewed.
            assertThat(viewModel.state.value.dailyStatistics).isEqualTo(emptyList())
        }

    @Test
    fun everyIntervalGetsItsOwnRowWithItsOwnTimes() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel =
                viewModelFor(
                    task(
                        intervals =
                            listOf(
                                interval("2026-09-09T12:00:00Z", minutes = 10, id = "early"),
                                interval("2026-09-09T12:30:00Z", minutes = 20, id = "late"),
                            ),
                    ),
                )

            val stats = viewModel.state.value.dailyStatistics
            // Newest first, one row each rather than one summed row for the day.
            assertThat(stats.map { it.intervalId }).isEqualTo(listOf("late", "early"))
            assertThat(stats.map { it.formattedDuration }).isEqualTo(listOf("00:20:00", "00:10:00"))
            assertThat(stats[0].formattedStartTime).isEqualTo(localClock("2026-09-09T12:30:00Z"))
            assertThat(stats[0].formattedEndTime).isEqualTo(localClock("2026-09-09T12:50:00Z"))
            assertThat(stats[1].formattedStartTime).isEqualTo(localClock("2026-09-09T12:00:00Z"))
            assertThat(stats[1].formattedEndTime).isEqualTo(localClock("2026-09-09T12:10:00Z"))
        }

    @Test
    fun aSliceCutAtMidnightEndsAtTwentyFour() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel =
                viewModelFor(
                    task(intervals = listOf(interval("2026-09-09T12:00:00Z", minutes = 24 * 60))),
                )

            val stats = viewModel.state.value.dailyStatistics
            // The older slice is the second row; it runs up to its day's midnight.
            assertThat(stats[1].formattedEndTime).isEqualTo("24:00")
            assertThat(stats[0].formattedStartTime).isEqualTo("00:00")
        }

    @Test
    fun editModeTogglesOnAndOff() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModelFor(task())

            viewModel.onAction(TaskDetailAction.OnEditModeClick)
            assertThat(viewModel.state.value.isEditMode).isTrue()

            viewModel.onAction(TaskDetailAction.OnCloseEditModeClick)
            assertThat(viewModel.state.value.isEditMode).isFalse()
        }

    @Test
    fun theParentProjectsColoursAreLoaded() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel =
                viewModelFor(
                    task(),
                    project = project().copy(colorArgb = 0xFF3F51B5.toInt(), useLightTextColor = true),
                )

            val state = viewModel.state.value
            assertThat(state.projectId).isEqualTo("project")
            assertThat(state.projectColor?.toArgb()).isEqualTo(0xFF3F51B5.toInt())
            assertThat(state.useLightTextColor).isTrue()
        }

    @Test
    fun aTaskWithSubTasksShowsTheirSum() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel =
                viewModelFor(
                    task(subTasks = listOf(sub("s1", millis = 60_000), sub("s2", millis = 30_000)))
                        .copy(durationMillis = 999_000),
                )

            // The figure Project Detail's task card shows, not the task's own banked total.
            assertThat(
                viewModel.state.value.task!!
                    .displayDurationMillis,
            ).isEqualTo(90_000)
        }

    @Test
    fun aRunningSubTaskTicksTheTaskDuration() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel =
                viewModelFor(
                    task(subTasks = listOf(sub("s1", millis = 60_000), sub("s2", millis = 30_000))),
                )

            running.startTimer(runningTimer(taskId = "task-0", subTaskId = "s2", bankedDuration = 45_000.milliseconds))

            val state = viewModel.state.value
            assertThat(state.task!!.displayDurationMillis).isEqualTo(105_000)
            assertThat(state.isTimerRunning).isTrue()
        }

    @Test
    fun aRowEmissionDoesNotDropTheLiveValue() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModelFor(task().copy(durationMillis = 10_000))
            running.startTimer(runningTimer(taskId = "task-0", bankedDuration = 25_000.milliseconds))

            // Any write to the row while the timer runs - an interval opening, a title saved.
            stored.value = stored.value!!.copy(title = "Renamed")

            assertThat(
                viewModel.state.value.task!!
                    .displayDurationMillis,
            ).isEqualTo(25_000)
            assertThat(viewModel.state.value.isTimerRunning).isTrue()
        }

    @Test
    fun resumingATaskWithSubTasksStartsTheLastStartedOne() =
        runTest(UnconfinedTestDispatcher()) {
            subTaskRepository.lastStarted = "s2"
            val viewModel = viewModelFor(task(subTasks = listOf(sub("s1"), sub("s2"))))

            viewModel.onAction(TaskDetailAction.OnToggleTimer)

            assertThat(subTaskRepository.started).isEqualTo(listOf("s2"))
            assertThat(taskRepository.started).isEqualTo(emptyList())
        }

    @Test
    fun resumingSkipsAFinishedSubTask() =
        runTest(UnconfinedTestDispatcher()) {
            subTaskRepository.lastStarted = "s1"
            val viewModel =
                viewModelFor(
                    task(subTasks = listOf(sub("s1").copy(isFinished = true), sub("s2"))),
                )

            viewModel.onAction(TaskDetailAction.OnToggleTimer)

            assertThat(subTaskRepository.started).isEqualTo(listOf("s2"))
        }

    @Test
    fun pausingATaskWithSubTasksStopsTheRunningSubTask() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModelFor(task(subTasks = listOf(sub("s1"), sub("s2"))))
            running.startTimer(runningTimer(taskId = "task-0", subTaskId = "s1"))

            viewModel.onAction(TaskDetailAction.OnToggleTimer)

            assertThat(subTaskRepository.stopped).isEqualTo(listOf("s1"))
            assertThat(taskRepository.stopped).isEqualTo(emptyList())
        }

    @Test
    fun aTaskWithoutSubTasksStartsAndStopsItself() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModelFor(task())

            viewModel.onAction(TaskDetailAction.OnToggleTimer)
            assertThat(taskRepository.started).isEqualTo(listOf("task-0"))
            assertThat(viewModel.state.value.isTimerRunning).isTrue()

            viewModel.onAction(TaskDetailAction.OnToggleTimer)
            assertThat(taskRepository.stopped).isEqualTo(listOf("task-0"))
            assertThat(viewModel.state.value.isTimerRunning).isFalse()
        }

    private fun sub(id: String, millis: Long = 0L) = subTask(id = id).copy(durationMillis = millis)

    @OptIn(ExperimentalTime::class)
    private fun localClock(instant: String): String =
        Instant.parse(instant).toLocalDateTime(TimeZone.currentSystemDefault()).time.let {
            it.hour.toString().padStart(2, '0') + ":" + it.minute.toString().padStart(2, '0')
        }

    private inner class FakeProjectTaskRepository : ProjectTaskRepository {
        val started = mutableListOf<String>()
        val stopped = mutableListOf<String>()

        override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = stored

        override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> = Result.Success(Unit)

        override suspend fun deleteProjectTask(projectId: String, taskId: String): EmptyResult<DataError> =
            Result.Success(Unit)

        override suspend fun updateProjectTaskDuration(
            taskId: String,
            newDurationMillis: Long,
        ): EmptyResult<DataError> = Result.Success(Unit)

        override suspend fun updateProjectTaskTitle(taskId: String, title: String): EmptyResult<DataError> =
            Result.Success(Unit)

        override suspend fun updateProjectTaskText(
            taskId: String,
            title: String,
            description: String?,
        ) = Result.Success(Unit)

        // Opening and closing the interval is what production's running-timer query reports.
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

        override suspend fun reorderTasks(projectId: String, orderedTaskIds: List<String>): EmptyResult<DataError> =
            Result.Success(Unit)

        override suspend fun syncPendingTasks(): EmptyResult<DataError> = Result.Success(Unit)
    }

    private inner class FakeSubTaskRepository : SubTaskRepository {
        val started = mutableListOf<String>()
        val stopped = mutableListOf<String>()
        var lastStarted: String? = null

        override fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>> = storedSubTasks

        override suspend fun upsertSubTask(subTask: ProjectSubTask): EmptyResult<DataError> = Result.Success(Unit)

        override suspend fun deleteSubTask(subTaskId: String): EmptyResult<DataError> = Result.Success(Unit)

        override suspend fun startSubTask(subTaskId: String): EmptyResult<DataError> {
            started += subTaskId
            running.startTimer(runningTimer(taskId = "task-0", subTaskId = subTaskId))
            return Result.Success(Unit)
        }

        override suspend fun stopSubTask(subTaskId: String): EmptyResult<DataError> {
            stopped += subTaskId
            running.stopTimer()
            return Result.Success(Unit)
        }

        override suspend fun lastStartedSubTaskId(taskId: String): Result<String?, DataError> =
            Result.Success(lastStarted)

        override suspend fun reorderSubTasks(taskId: String, orderedSubTaskIds: List<String>): EmptyResult<DataError> =
            Result.Success(Unit)

        override suspend fun syncPendingSubTasks(): EmptyResult<DataError> = Result.Success(Unit)
    }
}
