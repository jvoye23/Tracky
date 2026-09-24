@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.core.data.notification

import androidx.compose.ui.graphics.toArgb
import com.jvcs.tracky.core.domain.notification.TimerNotificationSession
import com.jvcs.tracky.designsystem.theme.defaultProjectColor
import com.jvcs.tracky.features.project.domain.timer.ProjectRef
import com.jvcs.tracky.features.project.domain.timer.TaskRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class IosTimerNotificationControllerTest {

    private val asOf = Instant.fromEpochSeconds(1_700_000_000)

    // The controller hops to Dispatchers.Main for ActivityKit. In a Kotlin/Native test binary the
    // real main dispatcher is the queue the test itself is blocking, so it has to be replaced or
    // the hop deadlocks.
    @BeforeTest
    fun replaceMainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun restoreMainDispatcher() = Dispatchers.resetMain()

    private fun session(
        colorArgb: Int? = ORANGE,
        useLightTextColor: Boolean = false,
        subTask: TaskRef? = TaskRef(id = "sub-1", title = "Auth endpoints"),
        elapsed: Duration = 90.minutes,
        isRunning: Boolean = true,
        isForeign: Boolean = false,
    ) = TimerNotificationSession(
        project = ProjectRef(id = "p-1", title = "Tracky App Redesign", colorArgb = colorArgb),
        useLightTextColor = useLightTextColor,
        task = TaskRef(id = "t-1", title = "Token refresh"),
        subTask = subTask,
        elapsed = elapsed,
        asOf = asOf,
        isRunning = isRunning,
        isForeign = isForeign,
    )

    /** The widget hides its toggle off this flag, so it has to survive the crossing into Swift. */
    @Test
    fun foreignnessCrossesToTheWidget() {
        assertTrue(session(isForeign = true).toLiveActivityState().isForeign)
        assertFalse(session().toLiveActivityState().isForeign)
    }

    @Test
    fun aRunningSessionStartsAtTheInstantItsElapsedTimeWouldHaveBegun() {
        val state = session(elapsed = 90.minutes).toLiveActivityState()

        // 90 minutes of banked time means the clock began 90 minutes before asOf.
        assertEquals(asOf.epochSeconds - 5_400.0, state.startedAtEpochSeconds)
        assertEquals(5_400.0, state.elapsedSeconds)
        assertTrue(state.isRunning)
    }

    @Test
    fun aPausedSessionKeepsItsElapsedSecondsAndOffersPlay() {
        val state = session(elapsed = 42.seconds, isRunning = false).toLiveActivityState()

        assertEquals(42.0, state.elapsedSeconds)
        assertFalse(state.isRunning)
    }

    @Test
    fun aProjectWithNoColourOfItsOwnFallsBackToTheDefault() {
        val state = session(colorArgb = null).toLiveActivityState()

        assertEquals(defaultProjectColor.toArgb(), state.accentArgb)
    }

    @Test
    fun aProjectWithAColourKeepsIt() {
        val state = session(colorArgb = ORANGE).toLiveActivityState()

        assertEquals(ORANGE, state.accentArgb)
    }

    @Test
    fun theThreeTitleLinesCarryThroughSeparately() {
        val state = session().toLiveActivityState()

        assertEquals("Tracky App Redesign", state.projectTitle)
        assertEquals("Token refresh", state.taskTitle)
        assertEquals("Auth endpoints", state.subTaskTitle)
        assertEquals("p-1", state.projectId)
    }

    @Test
    fun aTaskTimedOnItsOwnHasNoSubTaskLine() {
        val state = session(subTask = null).toLiveActivityState()

        assertNull(state.subTaskTitle)
        assertEquals("Token refresh", state.taskTitle)
    }

    @Test
    fun theProjectDecidesWhetherItsAccentNeedsLightTextOnTop() {
        assertTrue(session(useLightTextColor = true).toLiveActivityState().useLightTextColor)
        assertFalse(session(useLightTextColor = false).toLiveActivityState().useLightTextColor)
    }

    @Test
    fun showBeforeSwiftHasRegisteredABridgeIsHarmless() =
        runTest {
            // startKoinIos() starts the coordinator, and a timer left running by a previous launch
            // reaches the controller straight away, so this ordering really happens on a cold start.
            val controller = IosTimerNotificationController(bridge = { null })

            controller.show(session())
            controller.dismiss()
        }

    @Test
    fun showHandsTheMappedStateToTheBridge() =
        runTest {
            val bridge = RecordingBridge()

            IosTimerNotificationController(bridge = { bridge }).show(session(elapsed = 90.minutes))

            assertEquals(1, bridge.shown.size)
            assertEquals("Token refresh", bridge.shown.single().taskTitle)
            assertEquals(asOf.epochSeconds - 5_400.0, bridge.shown.single().startedAtEpochSeconds)
        }

    @Test
    fun dismissReachesTheBridge() =
        runTest {
            val bridge = RecordingBridge()

            IosTimerNotificationController(bridge = { bridge }).dismiss()

            assertEquals(1, bridge.dismissals)
        }

    private class RecordingBridge : LiveActivityBridge {
        val shown = mutableListOf<LiveActivityState>()
        var dismissals = 0

        override fun show(state: LiveActivityState) {
            shown += state
        }

        override fun dismiss() {
            dismissals++
        }
    }

    private companion object {
        /** The accent in Requirements/DesignRequirements/tracky_notification.png. */
        val ORANGE = 0xFFF39B19.toInt()
    }
}
