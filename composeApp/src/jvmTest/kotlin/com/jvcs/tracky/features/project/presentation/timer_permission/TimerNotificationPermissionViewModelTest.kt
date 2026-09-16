package com.jvcs.tracky.features.project.presentation.timer_permission

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.jvcs.tracky.core.domain.notification.TimerNotificationPermission
import com.jvcs.tracky.core.domain.notification.TimerNotificationPermissionRequester
import com.jvcs.tracky.features.project.domain.timer.ProjectRef
import com.jvcs.tracky.features.project.domain.timer.RunningTimer
import com.jvcs.tracky.features.project.domain.timer.RunningTimerRepository
import com.jvcs.tracky.features.project.domain.timer.TaskRef
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The ask happens once, on the first timer, and only a refusal is worth telling the user about.
 *
 * The "once" is the part with teeth: the trigger is a flow that flips back and forth all day as
 * timers start and stop, and the guard has to survive both that and a `WhileSubscribed` gap.
 */
internal class TimerNotificationPermissionViewModelTest {

    private val runningTimer = MutableStateFlow<RunningTimer?>(null)
    private val requester = FakeRequester()
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: TimerNotificationPermissionViewModel

    private val timer = RunningTimer(
        project = ProjectRef(id = "p1", title = "Tracky App", colorArgb = 0xFF7DA0B7.toInt()),
        useLightTextColor = true,
        task = TaskRef(id = "t1", title = "Token refresh"),
        subTask = null,
        startedAt = Instant.fromEpochMilliseconds(0),
        bankedDuration = 2.minutes
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        savedStateHandle = SavedStateHandle()
        viewModel = buildViewModel()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun buildViewModel() = TimerNotificationPermissionViewModel(
        permissionRequester = requester,
        runningTimerRepository = object : RunningTimerRepository {
            override fun observeRunningTimer(): Flow<RunningTimer?> = runningTimer
        },
        savedStateHandle = savedStateHandle
    )

    /** state is a `WhileSubscribed` stateIn, so nothing is observed until something collects. */
    private fun TestScope.subscribeToState() {
        backgroundScope.launch { viewModel.state.collect {} }
    }

    @Test
    fun nothingIsAskedWhileNoTimerIsRunning() = runTest(UnconfinedTestDispatcher()) {
        subscribeToState()

        assertEquals(0, requester.requestCount)
    }

    @Test
    fun theFirstRunningTimerTriggersTheAsk() = runTest(UnconfinedTestDispatcher()) {
        subscribeToState()

        runningTimer.value = timer

        assertEquals(1, requester.requestCount)
    }

    @Test
    fun aGrantIsNotWorthADialog() = runTest(UnconfinedTestDispatcher()) {
        requester.answer = TimerNotificationPermission.Granted

        viewModel.state.test {
            assertFalse(awaitItem().showDeniedDialog)
            runningTimer.value = timer
            expectNoEvents()
        }
    }

    @Test
    fun aPlatformWithNothingToAskNeverShowsTheDialog() = runTest(UnconfinedTestDispatcher()) {
        // The contract iOS and the JVM desktop build rest on.
        requester.answer = TimerNotificationPermission.NotRequired

        viewModel.state.test {
            assertFalse(awaitItem().showDeniedDialog)
            runningTimer.value = timer
            expectNoEvents()
        }
    }

    @Test
    fun aRefusalExplainsWhatWasLost() = runTest(UnconfinedTestDispatcher()) {
        requester.answer = TimerNotificationPermission.Denied

        viewModel.state.test {
            assertFalse(awaitItem().showDeniedDialog)
            runningTimer.value = timer
            assertTrue(awaitItem().showDeniedDialog)
        }
    }

    @Test
    fun aPermanentRefusalGetsTheSameExplanation() = runTest(UnconfinedTestDispatcher()) {
        requester.answer = TimerNotificationPermission.DeniedAlways

        viewModel.state.test {
            assertFalse(awaitItem().showDeniedDialog)
            runningTimer.value = timer
            assertTrue(awaitItem().showDeniedDialog)
        }
    }

    @Test
    fun aSecondTimerInTheSameSessionDoesNotAskAgain() = runTest(UnconfinedTestDispatcher()) {
        subscribeToState()

        runningTimer.value = timer
        runningTimer.value = null
        runningTimer.value = timer

        assertEquals(1, requester.requestCount)
    }

    @Test
    fun aViewModelRebuiltAfterProcessDeathDoesNotAskAgain() = runTest(UnconfinedTestDispatcher()) {
        subscribeToState()
        runningTimer.value = timer
        assertEquals(1, requester.requestCount)

        // Same SavedStateHandle, new ViewModel: what Android hands back after process death with a
        // timer still running. A plain field would ask a second time here.
        viewModel = buildViewModel()
        subscribeToState()

        assertEquals(1, requester.requestCount)
    }

    @Test
    fun confirmingDismissesWithoutLeavingForSettings() = runTest(UnconfinedTestDispatcher()) {
        requester.answer = TimerNotificationPermission.Denied
        subscribeToState()
        runningTimer.value = timer

        viewModel.onAction(TimerNotificationPermissionAction.OnConfirm)

        assertFalse(viewModel.state.value.showDeniedDialog)
        assertEquals(0, requester.openAppSettingsCount)
    }

    @Test
    fun openingSettingsCallsThroughAndDismisses() = runTest(UnconfinedTestDispatcher()) {
        requester.answer = TimerNotificationPermission.Denied
        subscribeToState()
        runningTimer.value = timer

        viewModel.onAction(TimerNotificationPermissionAction.OnOpenAppSettings)

        assertEquals(1, requester.openAppSettingsCount)
        assertFalse(viewModel.state.value.showDeniedDialog)
    }

    private class FakeRequester : TimerNotificationPermissionRequester {
        var answer: TimerNotificationPermission = TimerNotificationPermission.Granted
        var requestCount = 0
            private set
        var openAppSettingsCount = 0
            private set

        override suspend fun request(): TimerNotificationPermission {
            requestCount++
            return answer
        }

        override fun openAppSettings() {
            openAppSettingsCount++
        }
    }
}
