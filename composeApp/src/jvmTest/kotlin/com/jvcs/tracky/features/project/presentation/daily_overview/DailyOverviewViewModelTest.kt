@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.features.project.presentation.daily_overview

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.presentation.fakes.FakeProjectRepository
import com.jvcs.tracky.features.project.presentation.fakes.FixedTimeProvider
import com.jvcs.tracky.features.project.presentation.fakes.interval
import com.jvcs.tracky.features.project.presentation.fakes.project
import com.jvcs.tracky.features.project.presentation.fakes.task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
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
 * The screen reads the task tree once and derives everything from it, so the load-count
 * assertions here are the ones that matter: picking a day or a month must never go back to the
 * repository.
 *
 * The system zone decides which day "today" is, so every expectation is derived from the same
 * clock the ViewModel reads rather than hardcoded.
 */
class DailyOverviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone = TimeZone.currentSystemDefault()

    /** 2026-09-10T08:00:00Z, the plan's "today". */
    private val now = Instant.parse("2026-09-10T08:00:00Z")
    private val today get() = now.toLocalDateTime(zone).date

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    // --- loading ------------------------------------------------------------------------------

    @Test
    fun `the screen opens on today, with today's month showing`() = runTest {
        val state = viewModel().state.value

        assertEquals(today, state.selectedDate)
        assertEquals(YearMonth(today.year, today.month), state.months[state.visibleMonthIndex].yearMonth)
        assertFalse(state.isLoading)
    }

    @Test
    fun `the project's title and colour reach the state`() = runTest {
        val vm = viewModel(project = project().copy(title = "Tracky", colorArgb = 0xFFF39B19.toInt()))

        assertEquals("Tracky", vm.state.value.projectTitle)
        assertEquals(Color(0xFFF39B19.toInt()), vm.state.value.projectColor)
    }

    @Test
    fun `a preselected date outranks today`() = runTest {
        val target = LocalDate(2026, 9, 4)
        val vm = viewModel(preselected = target.toEpochDays())

        assertEquals(target, vm.state.value.selectedDate)
    }

    @Test
    fun `a restored date outranks the one navigation supplied`() = runTest {
        val restored = LocalDate(2026, 8, 20)
        val handle = SavedStateHandle(
            mapOf(DailyOverviewViewModel.KEY_SELECTED_DATE to restored.toEpochDays())
        )
        val vm = viewModel(preselected = LocalDate(2026, 9, 4).toEpochDays(), savedStateHandle = handle)

        assertEquals(restored, vm.state.value.selectedDate)
    }

    @Test
    fun `a project that cannot be read reports an error rather than hanging on the spinner`() = runTest {
        val repository = FakeProjectRepository(null)
        val vm = viewModel(repository = repository)

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.months.isEmpty())
    }

    @Test
    fun `a project with no intervals still opens on today`() = runTest {
        val vm = viewModel(project = project())

        assertEquals(today, vm.state.value.selectedDate)
        assertTrue(vm.state.value.dayDetail?.isEmpty == true)
        assertTrue(vm.state.value.months.isNotEmpty())
    }

    // --- deriving in memory --------------------------------------------------------------------

    @Test
    fun `selecting a date rebuilds the day without going back to the repository`() = runTest {
        val repository = FakeProjectRepository(trackedProject())
        val vm = viewModel(repository = repository)
        assertEquals(1, repository.treeReads)

        vm.onAction(DailyOverviewAction.OnDateSelected(LocalDate(2026, 9, 8)))
        advanceUntilIdle()

        assertEquals("Tue, Sep 08", vm.state.value.dayDetail?.dateLabel)
        assertEquals(1, vm.state.value.dayDetail?.intervalCount)
        // The whole point of holding the tree: no second read.
        assertEquals(1, repository.treeReads)
    }

    @Test
    fun `paging the calendar leaves the selected day alone`() = runTest {
        val repository = FakeProjectRepository(trackedProject())
        val vm = viewModel(repository = repository)
        val selectedBefore = vm.state.value.selectedDate

        vm.onAction(DailyOverviewAction.OnMonthChanged(0))
        advanceUntilIdle()

        assertEquals(0, vm.state.value.visibleMonthIndex)
        assertEquals(selectedBefore, vm.state.value.selectedDate)
        assertEquals(1, repository.treeReads)
    }

    @Test
    fun `a month index outside the range is clamped rather than crashing`() = runTest {
        val vm = viewModel(project = trackedProject())

        vm.onAction(DailyOverviewAction.OnMonthChanged(99))
        advanceUntilIdle()

        assertEquals(vm.state.value.months.lastIndex, vm.state.value.visibleMonthIndex)
    }

    @Test
    fun `selecting a date in another month pages the calendar to it`() = runTest {
        val vm = viewModel(project = trackedProject())
        val august = LocalDate(2026, 8, 4)

        vm.onAction(DailyOverviewAction.OnDateSelected(august))
        advanceUntilIdle()

        assertEquals(YearMonth(2026, 8), vm.state.value.months[vm.state.value.visibleMonthIndex].yearMonth)
    }

    @Test
    fun `the selected date is remembered for process death`() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(project = trackedProject(), savedStateHandle = handle)
        val target = LocalDate(2026, 9, 8)

        vm.onAction(DailyOverviewAction.OnDateSelected(target))
        advanceUntilIdle()

        assertEquals(target.toEpochDays(), handle.get<Long>(DailyOverviewViewModel.KEY_SELECTED_DATE))
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun trackedProject(): Project = project(
        tasks = listOf(
            task(
                title = "Design review",
                intervals = listOf(
                    interval("2026-08-04T09:00:00Z", minutes = 240, id = "aug"),
                    interval("2026-09-08T09:30:00Z", minutes = 42, id = "sep")
                )
            )
        )
    )

    private fun TestScope.viewModel(
        project: Project? = trackedProject(),
        repository: FakeProjectRepository = FakeProjectRepository(project),
        preselected: Long = DailyOverviewViewModel.NO_PRESELECTED_DATE,
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ): DailyOverviewViewModel {
        val vm = DailyOverviewViewModel(
            projectId = "project",
            preselectedDateEpochDay = preselected,
            projectRepository = repository,
            timeProvider = FixedTimeProvider(now),
            savedStateHandle = savedStateHandle,
            ioDispatcher = dispatcher
        )
        // Nothing loads until something subscribes: the read runs from `onStart`.
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        return vm
    }
}
