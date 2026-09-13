package com.jvcs.tracky.features.project.presentation.task_detail

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.testTimeManager
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.presentation.fakes.interval
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModelFor(task: ProjectTask): TaskDetailViewModel {
        stored.value = task
        val viewModel = TaskDetailViewModel(
            taskId = task.projectTaskId,
            projectTaskRepository = FakeProjectTaskRepository(),
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
        override suspend fun startProjectTask(taskId: String): EmptyResult<DataError> = Result.Success(Unit)
        override suspend fun stopProjectTask(taskId: String): EmptyResult<DataError> = Result.Success(Unit)
        override suspend fun syncPendingTasks() = Unit
    }
}
