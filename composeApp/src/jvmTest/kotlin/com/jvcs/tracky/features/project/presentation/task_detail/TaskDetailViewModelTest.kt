package com.jvcs.tracky.features.project.presentation.task_detail

import androidx.compose.ui.graphics.toArgb
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.testTimeManager
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Task Detail's "Daily sessions" table, one row per counted interval slice.
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

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModelFor(task: ProjectTask, project: Project? = null): TaskDetailViewModel {
        stored.value = task
        val viewModel = TaskDetailViewModel(
            taskId = task.projectTaskId,
            projectTaskRepository = FakeProjectTaskRepository(),
            projectRepository = FakeProjectRepository(project),
            timeManager = testTimeManager()
        )
        // state is a WhileSubscribed stateIn, so loadSession does not run until something collects.
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun aDayTotalIsFormattedWithoutCentiseconds() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModelFor(
            task(intervals = listOf(interval("2026-09-09T12:00:00Z", minutes = 30)))
        )

        // Three segments, not formatDuration's four: a day total is not a stopwatch reading, and
        // the unbounded-hours dialect is what let an impossible number render.
        assertEquals("00:30:00", viewModel.state.value.dailyStatistics.single().formattedDuration)
    }

    @Test
    fun aTaskWithSubTasksIsCountedThroughThemOnly() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModelFor(
            task(
                // The enclosing task interval a subtask timer opens. Counting it as well would
                // double-bill the same stretch of wall clock.
                intervals = listOf(interval("2026-09-09T12:00:00Z", minutes = 60)),
                subTasks = listOf(
                    subTask(intervals = listOf(subInterval("2026-09-09T12:00:00Z", minutes = 30)))
                )
            )
        )

        val stats = viewModel.state.value.dailyStatistics
        assertEquals(1, stats.size)
        assertEquals("00:30:00", stats.single().formattedDuration)
    }

    @Test
    fun anIntervalRunningPastMidnightIsSplitAcrossTheTwoDays() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModelFor(
            // Noon-to-noon: two calendar days in every zone, and exactly 24h banked.
            task(intervals = listOf(interval("2026-09-09T12:00:00Z", minutes = 24 * 60)))
        )

        val stats = viewModel.state.value.dailyStatistics
        assertEquals(2, stats.size, "one day cannot hold a 24-hour stretch that started at noon")
        // Still 24 hours in total, just no longer all on one row.
        assertEquals(
            24 * 60 * 60L,
            stats.sumOf { stat ->
                stat.formattedDuration.split(":").let { (h, m, s) ->
                    h.toLong() * 3600 + m.toLong() * 60 + s.toLong()
                }
            }
        )
    }

    @Test
    fun anOpenIntervalContributesNothing() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModelFor(
            task(intervals = listOf(interval("2026-09-09T12:00:00Z", minutes = 30, open = true)))
        )

        // A running timer banks nothing until it stops - and a stranded one banks nothing at all
        // until it is reviewed.
        assertEquals(emptyList(), viewModel.state.value.dailyStatistics)
    }

    @Test
    fun everyIntervalGetsItsOwnRowWithItsOwnTimes() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModelFor(
            task(
                intervals = listOf(
                    interval("2026-09-09T12:00:00Z", minutes = 10, id = "early"),
                    interval("2026-09-09T12:30:00Z", minutes = 20, id = "late"),
                )
            )
        )

        val stats = viewModel.state.value.dailyStatistics
        // Newest first, one row each rather than one summed row for the day.
        assertEquals(listOf("late", "early"), stats.map { it.intervalId })
        assertEquals(listOf("00:20:00", "00:10:00"), stats.map { it.formattedDuration })
        assertEquals(localClock("2026-09-09T12:30:00Z"), stats[0].formattedStartTime)
        assertEquals(localClock("2026-09-09T12:50:00Z"), stats[0].formattedEndTime)
        assertEquals(localClock("2026-09-09T12:00:00Z"), stats[1].formattedStartTime)
        assertEquals(localClock("2026-09-09T12:10:00Z"), stats[1].formattedEndTime)
    }

    @Test
    fun aSliceCutAtMidnightEndsAtTwentyFour() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModelFor(
            task(intervals = listOf(interval("2026-09-09T12:00:00Z", minutes = 24 * 60)))
        )

        val stats = viewModel.state.value.dailyStatistics
        // The older slice is the second row; it runs up to its day's midnight, which reads as
        // 24:00 rather than the next day's 00:00.
        assertEquals("24:00", stats[1].formattedEndTime)
        assertEquals("00:00", stats[0].formattedStartTime)
    }

    @Test
    fun editModeTogglesOnAndOff() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModelFor(task())

        viewModel.onAction(TaskDetailAction.OnEditModeClick)
        assertTrue(viewModel.state.value.isEditMode)

        // Closing and confirming both only drop the outline: the text itself is saved on the
        // edit-text screen, so there is nothing here to revert.
        viewModel.onAction(TaskDetailAction.OnCloseEditModeClick)
        assertFalse(viewModel.state.value.isEditMode)
    }

    @Test
    fun theParentProjectsColoursAreLoaded() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModelFor(
            task(),
            project = project().copy(colorArgb = 0xFF3F51B5.toInt(), useLightTextColor = true)
        )

        val state = viewModel.state.value
        // The header tint and the duration card need both, and the edit-text screen needs the id.
        assertEquals("project", state.projectId)
        assertEquals(0xFF3F51B5.toInt(), state.projectColor?.toArgb())
        assertTrue(state.useLightTextColor)
    }

    @OptIn(ExperimentalTime::class)
    private fun localClock(instant: String): String =
        Instant.parse(instant).toLocalDateTime(TimeZone.currentSystemDefault()).time.let {
            it.hour.toString().padStart(2, '0') + ":" + it.minute.toString().padStart(2, '0')
        }

    private inner class FakeProjectTaskRepository : ProjectTaskRepository {
        override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = stored
        override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> =
            Result.Success(Unit)
        override suspend fun deleteProjectTask(projectId: String, taskId: String): EmptyResult<DataError> =
            Result.Success(Unit)
        override suspend fun updateProjectTaskDuration(taskId: String, newDurationMillis: Long): EmptyResult<DataError> =
            Result.Success(Unit)
        override suspend fun updateProjectTaskTitle(taskId: String, title: String): EmptyResult<DataError> =
            Result.Success(Unit)
        override suspend fun updateProjectTaskText(taskId: String, title: String, description: String?) = Result.Success(Unit)
        override suspend fun startProjectTask(taskId: String): EmptyResult<DataError> = Result.Success(Unit)
        override suspend fun stopProjectTask(taskId: String): EmptyResult<DataError> = Result.Success(Unit)
        override suspend fun reorderTasks(projectId: String, orderedTaskIds: List<String>): EmptyResult<DataError> =
            Result.Success(Unit)
        override suspend fun syncPendingTasks() = Unit
    }
}
