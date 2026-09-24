package com.jvcs.tracky.features.project.presentation.stranded_timer

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.cash.turbine.test
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.timer.StrandedTimer
import com.jvcs.tracky.features.project.domain.timer.StrandedTimerRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class StrandedTimerViewModelTest {

    private val parked = MutableStateFlow(emptyList<StrandedTimer>())
    private val repository = FakeStrandedTimerRepository()
    private lateinit var viewModel: StrandedTimerViewModel

    private fun timer(id: String, hoursLong: Long = 75) =
        StrandedTimer(
            taskIntervalId = id,
            subTaskIntervalId = null,
            taskId = "t-$id",
            taskTitle = "Project Detail Screen",
            projectTitle = "Tracky App",
            subTaskTitle = null,
            startedAt = Instant.fromEpochMilliseconds(0),
            proposedEndAt = Instant.fromEpochMilliseconds(hoursLong * 60 * 60 * 1000L),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = StrandedTimerViewModel(repository)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * state is a `WhileSubscribed` [kotlinx.coroutines.flow.stateIn], so the repository is not
     * observed until something collects. Tests that drive actions rather than assert on emissions
     * still need that subscription, or the queue stays empty and every action is a no-op.
     */
    private fun TestScope.subscribeToState() {
        backgroundScope.launch { viewModel.state.collect {} }
    }

    @Test
    fun theQueueSurfacesOneItemAtATime() =
        runTest(UnconfinedTestDispatcher()) {
            viewModel.state.test {
                assertNull(awaitItem().current)
                parked.value = listOf(timer("a"), timer("b"))
                val state = awaitItem()
                assertEquals("a", state.current?.taskIntervalId)
                assertEquals(2, state.pending.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun keepingResolvesTheTopItemAndAdvancesToTheNext() =
        runTest(UnconfinedTestDispatcher()) {
            parked.value = listOf(timer("a"), timer("b"))
            viewModel.state.test {
                awaitItem()

                viewModel.onAction(StrandedTimerAction.OnKeep)

                assertEquals(listOf("a"), repository.kept.map { it.taskIntervalId })
                // The repository's flow is what drops it, so the dialog advances on its own.
                assertEquals(
                    "b",
                    viewModel.state.value.current
                        ?.taskIntervalId,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aFailedResolutionLeavesTheItemInTheQueueAndReportsIt() =
        runTest(UnconfinedTestDispatcher()) {
            parked.value = listOf(timer("a"))
            repository.failNext = true
            subscribeToState()

            viewModel.events.test {
                viewModel.onAction(StrandedTimerAction.OnKeep)

                assertTrue(awaitItem() is StrandedTimerEvent.Error)
                // Still there: a resolution that did not happen must not look like one.
                assertEquals(
                    "a",
                    viewModel.state.value.current
                        ?.taskIntervalId,
                )
                assertFalse(viewModel.state.value.isResolving)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun editingSeedsTheFieldWithTheOfferedDuration() =
        runTest(UnconfinedTestDispatcher()) {
            parked.value = listOf(timer("a"))
            viewModel.state.test {
                awaitItem()

                viewModel.onAction(StrandedTimerAction.OnBeginEditDuration)

                val state = viewModel.state.value
                assertTrue(state.isEditingDuration)
                assertEquals("75:00", state.editDurationState.text.toString())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun confirmingAnEditBanksWhatWasTyped() =
        runTest(UnconfinedTestDispatcher()) {
            subscribeToState()
            parked.value = listOf(timer("a"))
            viewModel.onAction(StrandedTimerAction.OnBeginEditDuration)
            viewModel.state.value.editDurationState
                .setTextAndPlaceCursorAtEnd("2:30")

            viewModel.onAction(StrandedTimerAction.OnConfirmEditedDuration)

            assertEquals(listOf(2.hours + 30.minutes), repository.keptDurations)
        }

    @Test
    fun anUnparsableDurationResolvesNothing() =
        runTest(UnconfinedTestDispatcher()) {
            subscribeToState()
            parked.value = listOf(timer("a"))
            viewModel.onAction(StrandedTimerAction.OnBeginEditDuration)
            viewModel.state.value.editDurationState
                .setTextAndPlaceCursorAtEnd("not a time")

            viewModel.onAction(StrandedTimerAction.OnConfirmEditedDuration)

            assertTrue(repository.keptDurations.isEmpty())
            assertEquals(
                "a",
                viewModel.state.value.current
                    ?.taskIntervalId,
            )
        }

    @Test
    fun aHalfTypedDurationDoesNotCarryOntoTheNextItem() =
        runTest(UnconfinedTestDispatcher()) {
            subscribeToState()
            parked.value = listOf(timer("a"), timer("b"))
            viewModel.onAction(StrandedTimerAction.OnBeginEditDuration)
            viewModel.state.value.editDurationState
                .setTextAndPlaceCursorAtEnd("9:99")

            parked.value = listOf(timer("b"))

            assertFalse(viewModel.state.value.isEditingDuration)
            assertEquals(
                "",
                viewModel.state.value.editDurationState.text
                    .toString(),
            )
        }

    @Test
    fun durationParsingAcceptsHoursAndMinutesAndRejectsNonsense() {
        assertEquals(3.hours + 7.minutes, StrandedTimerViewModel.parseHoursMinutes("3:07"))
        assertEquals(3.hours, StrandedTimerViewModel.parseHoursMinutes("3"))
        assertEquals(Duration.ZERO, StrandedTimerViewModel.parseHoursMinutes("0:00"))
        assertNull(StrandedTimerViewModel.parseHoursMinutes(""))
        assertNull(StrandedTimerViewModel.parseHoursMinutes("1:60"), "60 minutes is an hour")
        assertNull(StrandedTimerViewModel.parseHoursMinutes("-1:00"))
        assertNull(StrandedTimerViewModel.parseHoursMinutes("1:2:3"))
    }

    private inner class FakeStrandedTimerRepository : StrandedTimerRepository {
        val kept = mutableListOf<StrandedTimer>()
        val keptDurations = mutableListOf<Duration>()
        val discarded = mutableListOf<StrandedTimer>()
        var failNext = false

        override fun observeStrandedTimers(): Flow<List<StrandedTimer>> = parked

        private fun resolveOrFail(timer: StrandedTimer): EmptyResult<DataError> {
            if (failNext) return Result.Error(DataError.Local.UNKNOWN)
            parked.value = parked.value.filterNot { it.id == timer.id }
            return Result.Success(Unit)
        }

        override suspend fun keep(timer: StrandedTimer): EmptyResult<DataError> {
            kept += timer
            return resolveOrFail(timer)
        }

        override suspend fun keepWithDuration(timer: StrandedTimer, duration: Duration): EmptyResult<DataError> {
            keptDurations += duration
            return resolveOrFail(timer)
        }

        override suspend fun discard(timer: StrandedTimer): EmptyResult<DataError> {
            discarded += timer
            return resolveOrFail(timer)
        }
    }
}
