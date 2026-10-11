package com.jvcs.tracky.core.domain.notification

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
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
            assertThat(controller.shown.isEmpty()).isTrue()

            reconciled.complete(Unit)
            settle()

            assertThat(controller.shown.size).isEqualTo(1)
        }

    @Test
    fun aRunningTimerIsShownWithItsThreeTitleLines() =
        runTest {
            reconciled.complete(Unit)
            runningTimer.value = subTaskTimer
            coordinator()
            settle()

            val session = controller.shown.last()
            assertThat(session.project.title).isEqualTo("Tracky App Redesign")
            assertThat(session.task).isEqualTo(TaskRef(id = "t1", title = "Token refresh"))
            assertThat(session.subTask).isEqualTo(TaskRef(id = "s1", title = "Auth endpoints"))
            assertThat(session.project.colorArgb).isEqualTo(0xFF7DA0B7.toInt())
            assertThat(session.isRunning).isTrue()
        }

    @Test
    fun theClockCountsTheBankedTotalPlusTheOpenSpan() =
        runTest {
            reconciled.complete(Unit)
            timeProvider.now = Instant.fromEpochMilliseconds(3.minutes.inWholeMilliseconds)
            runningTimer.value = taskTimer
            coordinator()
            settle()

            assertThat(controller.shown.last().elapsed).isEqualTo(5.minutes)
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

            assertThat(controller.dismissed).isEqualTo(1)
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

            assertThat(stoppedTaskIds).isEqualTo(listOf("t1"))
            assertThat(controller.dismissed).isEqualTo(0)
            val session = controller.shown.last()
            assertThat(session.isRunning).isFalse()
            assertThat(session.elapsed).isEqualTo(5.minutes)
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

            assertThat(stoppedSubTaskIds).isEqualTo(listOf("s1"))
            assertThat(stoppedTaskIds.isEmpty()).isTrue()
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

            assertThat(startedSubTaskIds).isEqualTo(listOf("s1"))
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

            assertThat(stoppedTaskIds.isEmpty()).isTrue()
            assertThat(stoppedSubTaskIds.isEmpty()).isTrue()
            // And the card keeps showing it running, because it is.
            assertThat(controller.shown.last().isRunning).isTrue()
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

            assertThat(controller.shown.last().isForeign).isTrue()
        }

    @Test
    fun aTimerThisDeviceStartedIsNotMarkedForeign() =
        runTest {
            reconciled.complete(Unit)
            runningTimer.value = taskTimer
            coordinator()
            settle()

            assertThat(controller.shown.last().isForeign).isFalse()
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

            assertThat(startedTaskIds.isEmpty()).isTrue()
            assertThat(startedSubTaskIds.isEmpty()).isTrue()
        }

    @Test
    fun playDoesNothingWhenNothingWasPaused() =
        runTest {
            reconciled.complete(Unit)
            val coordinator = coordinator()
            settle()

            coordinator.onResume()
            settle()

            assertThat(startedSubTaskIds.isEmpty()).isTrue()
            assertThat(startedTaskIds.isEmpty()).isTrue()
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

        override suspend fun syncPendingTasks(): EmptyResult<DataError> = Result.Success(Unit)
    }

    private inner class FakeSubTaskRepository : SubTaskRepository {
        override suspend fun startSubTask(subTaskId: String): EmptyResult<DataError> =
            Result.Success(Unit).also { startedSubTaskIds += subTaskId }

        override suspend fun stopSubTask(subTaskId: String): EmptyResult<DataError> =
            Result.Success(Unit).also { stoppedSubTaskIds += subTaskId }

        override fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>> = flowOf(emptyList())

        override suspend fun upsertSubTask(subTask: ProjectSubTask) = Result.Success(Unit)

        override suspend fun deleteSubTask(subTaskId: String) = Result.Success(Unit)

        override suspend fun lastStartedSubTaskId(taskId: String): Result<String?, DataError> = Result.Success(null)

        override suspend fun reorderSubTasks(taskId: String, orderedSubTaskIds: List<String>) = Result.Success(Unit)

        override suspend fun syncPendingSubTasks(): EmptyResult<DataError> = Result.Success(Unit)
    }
}
