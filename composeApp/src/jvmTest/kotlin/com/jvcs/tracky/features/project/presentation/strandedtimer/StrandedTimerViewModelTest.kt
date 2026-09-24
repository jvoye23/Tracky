package com.jvcs.tracky.features.project.presentation.strandedtimer

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
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
                assertThat(awaitItem().current).isNull()
                parked.value = listOf(timer("a"), timer("b"))
                val state = awaitItem()
                assertThat(state.current?.taskIntervalId).isEqualTo("a")
                assertThat(state.pending.size).isEqualTo(2)
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

                assertThat(repository.kept.map { it.taskIntervalId }).isEqualTo(listOf("a"))
                // The repository's flow is what drops it, so the dialog advances on its own.
                assertThat(
                    viewModel.state.value.current
                        ?.taskIntervalId,
                ).isEqualTo("b")
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

                assertThat(awaitItem() is StrandedTimerEvent.Error).isTrue()
                // Still there: a resolution that did not happen must not look like one.
                assertThat(
                    viewModel.state.value.current
                        ?.taskIntervalId,
                ).isEqualTo("a")
                assertThat(viewModel.state.value.isResolving).isFalse()
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
                assertThat(state.isEditingDuration).isTrue()
                assertThat(state.editDurationState.text.toString()).isEqualTo("75:00")
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

            assertThat(repository.keptDurations).isEqualTo(listOf(2.hours + 30.minutes))
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

            assertThat(repository.keptDurations.isEmpty()).isTrue()
            assertThat(
                viewModel.state.value.current
                    ?.taskIntervalId,
            ).isEqualTo("a")
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

            assertThat(viewModel.state.value.isEditingDuration).isFalse()
            assertThat(
                viewModel.state.value.editDurationState.text
                    .toString(),
            ).isEqualTo("")
        }

    @Test
    fun durationParsingAcceptsHoursAndMinutesAndRejectsNonsense() {
        assertThat(StrandedTimerViewModel.parseHoursMinutes("3:07")).isEqualTo(3.hours + 7.minutes)
        assertThat(StrandedTimerViewModel.parseHoursMinutes("3")).isEqualTo(3.hours)
        assertThat(StrandedTimerViewModel.parseHoursMinutes("0:00")).isEqualTo(Duration.ZERO)
        assertThat(StrandedTimerViewModel.parseHoursMinutes("")).isNull()
        assertThat(StrandedTimerViewModel.parseHoursMinutes("1:60"), name = "60 minutes is an hour").isNull()
        assertThat(StrandedTimerViewModel.parseHoursMinutes("-1:00")).isNull()
        assertThat(StrandedTimerViewModel.parseHoursMinutes("1:2:3")).isNull()
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
