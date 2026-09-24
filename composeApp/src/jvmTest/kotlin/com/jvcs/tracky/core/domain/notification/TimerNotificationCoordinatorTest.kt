package com.jvcs.tracky.core.domain.notification

import com.jvcs.tracky.core.domain.startup.StartupReconciliation
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.testServerClock
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.domain.timer.ProjectRef
import com.jvcs.tracky.features.project.domain.timer.RunningTimer
import com.jvcs.tracky.features.project.domain.timer.RunningTimerRepository
import com.jvcs.tracky.features.project.domain.timer.TaskRef
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The notification's half of the timer: what is shown, and what the two buttons write back.
 *
 * Pause is the interesting one. It closes the interval, so the running-timer flow immediately says
 * nothing is running - and the notification still has to stay up, frozen, or the user loses the
 * only way to resume from outside the app.
 */
internal class TimerNotificationCoordinatorTest {

    private val runningTimer = MutableStateFlow<RunningTimer?>(null)
    private val controller = RecordingController()
    private val timeProvider = FakeTimeProvider()
    private val reconciled = CompletableDeferred<Unit>()
    private val startedTaskIds = mutableListOf<String>()
    private val stoppedTaskIds = mutableListOf<String>()
    private val startedSubTaskIds = mutableListOf<String>()
    private val stoppedSubTaskIds = mutableListOf<String>()

    private val taskTimer =
        RunningTimer(
            project = ProjectRef(id = "p1", title = "Tracky App Redesign", colorArgb = 0xFF7DA0B7.toInt()),
            useLightTextColor = true,
            task = TaskRef(id = "t1", title = "Token refresh"),
            subTask = null,
            startedAt = Instant.fromEpochMilliseconds(0),
            bankedDuration = 2.minutes,
        )
    private val subTaskTimer = taskTimer.copy(subTask = TaskRef(id = "s1", title = "Auth endpoints"))

    private fun TestScope.coordinator(): TimerNotificationCoordinator {
        val coordinator =
            TimerNotificationCoordinator(
                runningTimerRepository =
                    object : RunningTimerRepository {
                        override fun observeRunningTimer(): Flow<RunningTimer?> = runningTimer
                    },
                projectTaskRepository = FakeProjectTaskRepository(),
                subTaskRepository = FakeSubTaskRepository(),
                controller = controller,
                startupReconciliation =
                    object : StartupReconciliation {
                        override suspend fun awaitReconciled() = reconciled.await()
                    },
                serverClock = testServerClock(timeProvider),
                applicationScope = backgroundScope,
            )
        coordinator.start()
        return coordinator
    }

    /**
     * advanceUntilIdle() on its own does not dispatch backgroundScope work: called from inside the
     * test coroutine it never yields, so coroutines queued on the same dispatcher never get to run.
     * A real suspension is what hands them the thread.
     */
    private suspend fun TestScope.settle() {
        yield()
        advanceUntilIdle()
    }

    @Test
    fun nothingIsShownUntilTheStrandedTimerPassHasFinished() =
        runTest {
            runningTimer.value = taskTimer
            coordinator()

            settle()

            // A timer still open at start-up may be about to be parked. Showing it would put a
            // stranded interval in the notification and let Pause bank every hour since it opened.
            assertTrue(controller.shown.isEmpty())

            reconciled.complete(Unit)
            settle()

            assertEquals(1, controller.shown.size)
        }

    @Test
    fun aRunningTimerIsShownWithItsThreeTitleLines() =
        runTest {
            reconciled.complete(Unit)
            runningTimer.value = subTaskTimer
            coordinator()
            settle()

            val session = controller.shown.last()
            assertEquals("Tracky App Redesign", session.project.title)
            assertEquals(TaskRef(id = "t1", title = "Token refresh"), session.task)
            assertEquals(TaskRef(id = "s1", title = "Auth endpoints"), session.subTask)
            assertEquals(0xFF7DA0B7.toInt(), session.project.colorArgb)
            assertTrue(session.isRunning)
        }

    @Test
    fun theClockCountsTheBankedTotalPlusTheOpenSpan() =
        runTest {
            reconciled.complete(Unit)
            timeProvider.now = Instant.fromEpochMilliseconds(3.minutes.inWholeMilliseconds)
            runningTimer.value = taskTimer
            coordinator()
            settle()

            assertEquals(5.minutes, controller.shown.last().elapsed)
        }

    @Test
    fun aTimerStoppedInTheAppTakesTheNotificationDownWithIt() =
        runTest {
            reconciled.complete(Unit)
            runningTimer.value = taskTimer
            coordinator()
            settle()

            runningTimer.value = null
            settle()

            assertEquals(1, controller.dismissed)
        }

    @Test
    fun pauseStopsTheTaskAndLeavesTheNotificationUpFrozen() =
        runTest {
            reconciled.complete(Unit)
            runningTimer.value = taskTimer
            val coordinator = coordinator()
            settle()
            timeProvider.now = Instant.fromEpochMilliseconds(3.minutes.inWholeMilliseconds)

            coordinator.onPause()
            // Closing the interval is what makes the running-timer flow go quiet.
            runningTimer.value = null
            settle()

            assertEquals(listOf("t1"), stoppedTaskIds)
            assertEquals(0, controller.dismissed)
            val session = controller.shown.last()
            assertFalse(session.isRunning)
            assertEquals(5.minutes, session.elapsed)
        }

    @Test
    fun pauseStopsTheSubTaskWhenOneIsTheThingRunning() =
        runTest {
            reconciled.complete(Unit)
            runningTimer.value = subTaskTimer
            val coordinator = coordinator()
            settle()

            coordinator.onPause()
            settle()

            assertEquals(listOf("s1"), stoppedSubTaskIds)
            assertTrue(stoppedTaskIds.isEmpty())
        }

    @Test
    fun playOpensANewIntervalOnWhatWasPaused() =
        runTest {
            reconciled.complete(Unit)
            runningTimer.value = subTaskTimer
            val coordinator = coordinator()
            settle()
            coordinator.onPause()
            runningTimer.value = null
            settle()

            coordinator.onResume()
            settle()

            assertEquals(listOf("s1"), startedSubTaskIds)
        }

    @Test
    fun pauseDoesNothingToATimerAnotherDeviceIsRunning() =
        runTest {
            // Pause is stop-then-start, so pausing a foreign timer would globally stop it — that is
            // Stop, not Pause. The surfaces hide the button; this is the belt to that's braces.
            reconciled.complete(Unit)
            runningTimer.value = taskTimer.copy(isForeign = true)
            val coordinator = coordinator()
            settle()

            coordinator.onPause()
            settle()

            assertTrue(stoppedTaskIds.isEmpty())
            assertTrue(stoppedSubTaskIds.isEmpty())
            // And the card keeps showing it running, because it is.
            assertTrue(controller.shown.last().isRunning)
        }

    /**
     * The flag has to reach the session or neither surface can hide anything: the notification and
     * the Live Activity both read it from there and nowhere else.
     */
    @Test
    fun tellsTheSurfacesWhenTheTimerIsAnotherDevices() =
        runTest {
            reconciled.complete(Unit)
            runningTimer.value = taskTimer.copy(isForeign = true)
            coordinator()
            settle()

            assertTrue(controller.shown.last().isForeign)
        }

    @Test
    fun aTimerThisDeviceStartedIsNotMarkedForeign() =
        runTest {
            reconciled.complete(Unit)
            runningTimer.value = taskTimer
            coordinator()
            settle()

            assertFalse(controller.shown.last().isForeign)
        }

    @Test
    fun playIsAbandonedWhenAnotherDeviceTookTheTimerOverWhilePaused() =
        runTest {
            // Resuming would supersede that device's timer from a snapshot minutes out of date. What
            // prevents it is the collector, not a check in onResume: any running timer clears `paused`,
            // so the frozen card is replaced by the foreign one before Resume can do anything.
            reconciled.complete(Unit)
            runningTimer.value = taskTimer
            val coordinator = coordinator()
            settle()
            coordinator.onPause()
            runningTimer.value = null
            settle()
            // The other device starts something while this one sits paused.
            runningTimer.value = subTaskTimer.copy(isForeign = true)
            settle()

            coordinator.onResume()
            settle()

            assertTrue(startedTaskIds.isEmpty())
            assertTrue(startedSubTaskIds.isEmpty())
        }

    @Test
    fun playDoesNothingWhenNothingWasPaused() =
        runTest {
            reconciled.complete(Unit)
            val coordinator = coordinator()
            settle()

            coordinator.onResume()
            settle()

            assertTrue(startedSubTaskIds.isEmpty())
            assertTrue(startedTaskIds.isEmpty())
        }

    private class RecordingController : TimerNotificationController {
        val shown = mutableListOf<TimerNotificationSession>()
        var dismissed = 0
            private set

        override suspend fun show(session: TimerNotificationSession) {
            shown += session
        }

        override suspend fun dismiss() {
            dismissed++
        }
    }

    private inner class FakeProjectTaskRepository : ProjectTaskRepository {
        override suspend fun startProjectTask(taskId: String): EmptyResult<DataError> =
            Result.Success(Unit).also { startedTaskIds += taskId }

        override suspend fun stopProjectTask(taskId: String): EmptyResult<DataError> =
            Result.Success(Unit).also { stoppedTaskIds += taskId }

        override suspend fun reorderTasks(projectId: String, orderedTaskIds: List<String>) = Result.Success(Unit)

        override suspend fun upsertProjectTask(projectTask: ProjectTask) = Result.Success(Unit)

        override suspend fun deleteProjectTask(projectId: String, taskId: String) = Result.Success(Unit)

        override suspend fun updateProjectTaskDuration(taskId: String, newDurationMillis: Long) = Result.Success(Unit)

        override suspend fun updateProjectTaskTitle(taskId: String, title: String) = Result.Success(Unit)

        override suspend fun updateProjectTaskText(
            taskId: String,
            title: String,
            description: String?,
        ) = Result.Success(Unit)

        override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = flowOf(null)

        override suspend fun syncPendingTasks() = Unit
    }

    private inner class FakeSubTaskRepository : SubTaskRepository {
        override suspend fun startSubTask(subTaskId: String): EmptyResult<DataError> =
            Result.Success(Unit).also { startedSubTaskIds += subTaskId }

        override suspend fun stopSubTask(subTaskId: String): EmptyResult<DataError> =
            Result.Success(Unit).also { stoppedSubTaskIds += subTaskId }

        override fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>> = flowOf(emptyList())

        override suspend fun upsertSubTask(subTask: ProjectSubTask) = Result.Success(Unit)

        override suspend fun deleteSubTask(subTaskId: String) = Result.Success(Unit)

        override suspend fun lastStartedSubTaskId(taskId: String): String? = null

        override suspend fun reorderSubTasks(taskId: String, orderedSubTaskIds: List<String>) = Result.Success(Unit)

        override suspend fun syncPendingSubTasks() = Unit
    }
}
