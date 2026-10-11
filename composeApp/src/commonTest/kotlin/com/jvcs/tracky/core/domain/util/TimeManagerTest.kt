package com.jvcs.tracky.core.domain.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.sync.SyncRecency
import com.jvcs.tracky.features.project.domain.timer.ProjectRef
import com.jvcs.tracky.features.project.domain.timer.RunningTimer
import com.jvcs.tracky.features.project.domain.timer.TaskRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * TimeManager renders the running timer; it does not own one.
 *
 * That is the whole point: the database row is the only thing that says a timer is running, so a
 * Pause pressed in the notification reaches the screens without anyone telling them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TimeManagerTest {

    private val running = FakeRunningTimerRepository()
    private val timeProvider = FakeTimeProvider()

    private val taskTimer =
        RunningTimer(
            project = ProjectRef(id = "p1", title = "Tracky App Redesign", colorArgb = null),
            useLightTextColor = false,
            task = TaskRef(id = "t1", title = "Token refresh"),
            subTask = null,
            startedAt = Instant.fromEpochMilliseconds(0),
            bankedDuration = 2.minutes,
        )
    private val subTaskTimer = taskTimer.copy(subTask = TaskRef(id = "s1", title = "Auth endpoints"))

    private val syncRecency = SyncRecency()

    private fun TestScope.timeManager() =
        testTimeManager(repository = running, timeProvider = timeProvider, syncRecency = syncRecency)

    /**
     * runCurrent(), not advanceUntilIdle(): the ticker is an endless delay loop, so draining the
     * scheduler would never return. The yield is what hands the thread to backgroundScope at all.
     */
    private suspend fun TestScope.settle() {
        yield()
        testScheduler.runCurrent()
    }

    @Test
    fun nothingRunningMeansNoTimerState() =
        runTest {
            val timeManager = timeManager()
            settle()

            assertThat(timeManager.taskStates.value).isEqualTo(emptyMap())
        }

    @Test
    fun aRunningTaskIsKeyedByItsTaskId() =
        runTest {
            running.startTimer(taskTimer)
            val timeManager = timeManager()
            settle()

            assertThat(timeManager.taskStates.value.keys).isEqualTo(setOf("t1"))
            assertThat(
                timeManager.taskStates.value
                    .getValue("t1")
                    .isRunning,
            ).isTrue()
        }

    @Test
    fun aRunningSubTaskIsKeyedBySubTaskIdNotItsParent() =
        runTest {
            // Timing a subtask opens the enclosing task interval too, but only one timer is running.
            running.startTimer(subTaskTimer)
            val timeManager = timeManager()
            settle()

            assertThat(timeManager.taskStates.value.keys).isEqualTo(setOf("s1"))
        }

    @Test
    fun theClockIsWhatWasBankedPlusTheOpenSpan() =
        runTest {
            timeProvider.now = Instant.fromEpochMilliseconds(90.seconds.inWholeMilliseconds)
            running.startTimer(taskTimer)
            val timeManager = timeManager()
            settle()

            // The notification derives its number the same way, from the same row.
            assertThat(
                timeManager.taskStates.value
                    .getValue("t1")
                    .totalDuration,
            ).isEqualTo(3.minutes + 30.seconds)
            assertThat(
                timeManager.taskStates.value
                    .getValue("t1")
                    .formattedTime,
            ).isEqualTo("00:03:30")
        }

    @Test
    fun anIntervalClosedAnywhereElseStopsTheClock() =
        runTest {
            // Pause in the notification closes the interval. Nothing tells TimeManager; it just stops.
            running.startTimer(taskTimer)
            val timeManager = timeManager()
            settle()
            assertThat(timeManager.taskStates.value.isNotEmpty()).isTrue()

            running.stopTimer()
            settle()

            assertThat(timeManager.taskStates.value).isEqualTo(emptyMap())

            // Reading straight after the row clears only proves the map went empty - the null wins
            // that read even if the old ticker is still looping. Advancing past several tick
            // boundaries is what tells a torn-down loop from one that merely lost the race.
            advanceTimeBy(5_000)
            settle()

            assertThat(timeManager.taskStates.value).isEqualTo(emptyMap())
        }

    @Test
    fun theClockAdvancesEverySecond() =
        runTest {
            running.startTimer(taskTimer)
            val timeManager = timeManager()
            settle()
            assertThat(
                timeManager.taskStates.value
                    .getValue("t1")
                    .formattedTime,
            ).isEqualTo("00:02:00")

            timeProvider.now = Instant.fromEpochMilliseconds(1_000)
            advanceTimeBy(1_001)
            settle()

            assertThat(
                timeManager.taskStates.value
                    .getValue("t1")
                    .formattedTime,
            ).isEqualTo("00:02:01")
        }

    @Test
    fun aForeignTimerFreezesOnceTheServerHasNotBeenHeardFromForTooLong() =
        runTest {
            // The hole this closes: a timer on the user's other phone keeps ticking here forever, and
            // stays *correct* while doing it — but if that phone stopped it an hour ago and this one
            // has not pulled since, the growing number is a confident lie.
            running.startTimer(taskTimer.copy(isForeign = true))
            syncRecency.markSynced(Instant.fromEpochMilliseconds(0))
            val timeManager = timeManager()
            settle()

            // Sixteen minutes later, with no successful pull in between.
            timeProvider.now = Instant.fromEpochMilliseconds(16.minutes.inWholeMilliseconds)
            advanceTimeBy(16.minutes.inWholeMilliseconds + 1)
            settle()

            val tick = timeManager.tick.value!!
            assertThat(tick.isStale).isTrue()
            // Frozen at what it read when the server was last heard from, not at "now".
            assertThat(tick.elapsed).isEqualTo(2.minutes)
        }

    @Test
    fun aForeignTimerKeepsTickingWhileSyncIsRecent() =
        runTest {
            // One failed pull must not freeze a healthy timer; the threshold is three pull cycles.
            running.startTimer(taskTimer.copy(isForeign = true))
            syncRecency.markSynced(Instant.fromEpochMilliseconds(0))
            val timeManager = timeManager()
            settle()

            timeProvider.now = Instant.fromEpochMilliseconds(5.minutes.inWholeMilliseconds)
            advanceTimeBy(5.minutes.inWholeMilliseconds + 1)
            settle()

            val tick = timeManager.tick.value!!
            assertThat(tick.isStale).isFalse()
            assertThat(tick.elapsed).isEqualTo(7.minutes)
        }

    @Test
    fun aTimerThisDeviceStartedNeverGoesStale() =
        runTest {
            // There is nothing to confirm: it is running because this device is running it. Freezing
            // it would break the offline case the whole design exists to protect.
            running.startTimer(taskTimer)
            syncRecency.markSynced(Instant.fromEpochMilliseconds(0))
            val timeManager = timeManager()
            settle()

            timeProvider.now = Instant.fromEpochMilliseconds(60.minutes.inWholeMilliseconds)
            advanceTimeBy(60.minutes.inWholeMilliseconds + 1)
            settle()

            val tick = timeManager.tick.value!!
            assertThat(tick.isStale).isFalse()
            assertThat(tick.elapsed).isEqualTo(62.minutes)
        }

    @Test
    fun aForeignTimerTicksWhenThisProcessHasNeverSynced() =
        runTest {
            // A cold start has not synced by definition. Treating that as stale would freeze every
            // adopted timer on launch, which is the opposite of the intent.
            running.startTimer(taskTimer.copy(isForeign = true))
            val timeManager = timeManager()
            settle()

            timeProvider.now = Instant.fromEpochMilliseconds(60.minutes.inWholeMilliseconds)
            advanceTimeBy(60.minutes.inWholeMilliseconds + 1)
            settle()

            assertThat(timeManager.tick.value!!.isStale).isFalse()
        }
}
