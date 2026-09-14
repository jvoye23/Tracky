package com.jvcs.tracky.core.domain.util

import com.jvcs.tracky.features.project.domain.timer.RunningTimer
import com.jvcs.tracky.features.project.domain.timer.RunningTimerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration
import kotlin.time.Instant

/** Drives TimeManager from a test: set [running] to say what the database would be reporting. */
internal class FakeRunningTimerRepository(
    val running: MutableStateFlow<RunningTimer?> = MutableStateFlow(null)
) : RunningTimerRepository {
    override fun observeRunningTimer(): Flow<RunningTimer?> = running
}

/**
 * A TimeManager for tests that only need one to exist. backgroundScope, not the test scope: the
 * ticker is an endless loop and a live job on the test scope would keep runTest from completing.
 */
internal fun TestScope.testTimeManager(
    repository: RunningTimerRepository = FakeRunningTimerRepository(),
    timeProvider: TimeProvider = FakeTimeProvider()
) = TimeManager(
    runningTimerRepository = repository,
    timeProvider = timeProvider,
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
    projectId = "project",
    projectTitle = "Project",
    projectColorArgb = null,
    useLightTextColor = false,
    taskId = taskId,
    taskTitle = "Task",
    subTaskId = subTaskId,
    subTaskTitle = subTaskId?.let { "Sub task" },
    startedAt = startedAt,
    bankedDuration = bankedDuration
)
