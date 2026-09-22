package com.jvcs.tracky.core.domain.util

import com.jvcs.tracky.features.project.domain.timer.ProjectRef
import com.jvcs.tracky.features.project.domain.timer.RunningTimer
import com.jvcs.tracky.core.domain.sync.SyncRecency
import com.jvcs.tracky.features.project.domain.timer.RunningTimerRepository
import com.jvcs.tracky.features.project.domain.timer.TaskRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration
import kotlin.time.Instant

/** Drives TimeManager from a test: start and stop a timer the way the database row would. */
internal class FakeRunningTimerRepository : RunningTimerRepository {
    private val running = MutableStateFlow<RunningTimer?>(null)
    override fun observeRunningTimer(): Flow<RunningTimer?> = running

    fun startTimer(timer: RunningTimer) {
        running.value = timer
    }

    fun stopTimer() {
        running.value = null
    }
}

/**
 * A TimeManager for tests that only need one to exist. backgroundScope, not the test scope: the
 * ticker is an endless loop and a live job on the test scope would keep runTest from completing.
 */
internal fun TestScope.testTimeManager(
    repository: RunningTimerRepository = FakeRunningTimerRepository(),
    timeProvider: TimeProvider = FakeTimeProvider(),
    syncRecency: SyncRecency = SyncRecency()
) = TimeManager(
    runningTimerRepository = repository,
    serverClock = testServerClock(timeProvider),
    syncRecency = syncRecency,
    scope = backgroundScope
)

/**
 * A running timer the way the database would report one. Fakes that stand in for the task and
 * subtask repositories publish this when they open an interval, so a test exercises the same path
 * production does: the row says what is running, and TimeManager only renders it.
 */
internal fun runningTimer(
    taskId: String,
    subTaskId: String? = null,
    startedAt: Instant = Instant.fromEpochMilliseconds(0),
    bankedDuration: Duration = Duration.ZERO
) = RunningTimer(
    project = ProjectRef(id = "project", title = "Project", colorArgb = null),
    useLightTextColor = false,
    task = TaskRef(id = taskId, title = "Task"),
    subTask = subTaskId?.let { TaskRef(id = it, title = "Sub task") },
    startedAt = startedAt,
    bankedDuration = bankedDuration
)
