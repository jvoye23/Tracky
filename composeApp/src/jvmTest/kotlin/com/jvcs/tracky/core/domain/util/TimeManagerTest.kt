package com.jvcs.tracky.core.domain.util

import com.jvcs.tracky.features.project.domain.timer.RunningTimer
import com.jvcs.tracky.features.project.domain.timer.RunningTimerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * TimeManager renders the running timer; it does not own one.
 *
 * That is the whole point: the database row is the only thing that says a timer is running, so a
 * Pause pressed in the notification reaches the screens without anyone telling them.
 */
internal class TimeManagerTest {

    private val running = MutableStateFlow<RunningTimer?>(null)
    private val timeProvider = FakeTimeProvider()

    private val taskTimer = RunningTimer(
        projectId = "p1",
        projectTitle = "Tracky App Redesign",
        projectColorArgb = null,
        useLightTextColor = false,
        taskId = "t1",
        taskTitle = "Token refresh",
        subTaskId = null,
        subTaskTitle = null,
        startedAt = Instant.fromEpochMilliseconds(0),
        bankedDuration = 2.minutes
    )
    private val subTaskTimer = taskTimer.copy(subTaskId = "s1", subTaskTitle = "Auth endpoints")

    private fun TestScope.timeManager() = TimeManager(
        runningTimerRepository = object : RunningTimerRepository {
            override fun observeRunningTimer(): Flow<RunningTimer?> = running
        },
        timeProvider = timeProvider,
        scope = backgroundScope
    )

    /**
     * runCurrent(), not advanceUntilIdle(): the ticker is an endless delay loop, so draining the
     * scheduler would never return. The yield is what hands the thread to backgroundScope at all.
     */
    private suspend fun TestScope.settle() {
        yield()
        testScheduler.runCurrent()
    }

    @Test
    fun nothingRunningMeansNoTimerState() = runTest {
        val timeManager = timeManager()
        settle()

        assertEquals(emptyMap(), timeManager.taskStates.value)
    }

    @Test
    fun aRunningTaskIsKeyedByItsTaskId() = runTest {
        running.value = taskTimer
        val timeManager = timeManager()
        settle()

        assertEquals(setOf("t1"), timeManager.taskStates.value.keys)
        assertTrue(timeManager.taskStates.value.getValue("t1").isRunning)
    }

    @Test
    fun aRunningSubTaskIsKeyedBySubTaskIdNotItsParent() = runTest {
        // Timing a subtask opens the enclosing task interval too, but only one timer is running.
        running.value = subTaskTimer
        val timeManager = timeManager()
        settle()

        assertEquals(setOf("s1"), timeManager.taskStates.value.keys)
    }

    @Test
    fun theClockIsWhatWasBankedPlusTheOpenSpan() = runTest {
        timeProvider.now = Instant.fromEpochMilliseconds(90.seconds.inWholeMilliseconds)
        running.value = taskTimer
        val timeManager = timeManager()
        settle()

        // The notification derives its number the same way, from the same row.
        assertEquals(3.minutes + 30.seconds, timeManager.taskStates.value.getValue("t1").totalDuration)
        assertEquals("00:03:30", timeManager.taskStates.value.getValue("t1").formattedTime)
    }

    @Test
    fun anIntervalClosedAnywhereElseStopsTheClock() = runTest {
        // Pause in the notification closes the interval. Nothing tells TimeManager; it just stops.
        running.value = taskTimer
        val timeManager = timeManager()
        settle()
        assertTrue(timeManager.taskStates.value.isNotEmpty())

        running.value = null
        settle()

        assertEquals(emptyMap(), timeManager.taskStates.value)
    }

    @Test
    fun theClockAdvancesEverySecond() = runTest {
        running.value = taskTimer
        val timeManager = timeManager()
        settle()
        assertEquals("00:02:00", timeManager.taskStates.value.getValue("t1").formattedTime)

        timeProvider.now = Instant.fromEpochMilliseconds(1_000)
        advanceTimeBy(1_001)
        settle()

        assertEquals("00:02:01", timeManager.taskStates.value.getValue("t1").formattedTime)
    }
}
