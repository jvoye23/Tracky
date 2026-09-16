package com.jvcs.tracky.core.domain.notification

import com.jvcs.tracky.core.domain.startup.StartupReconciliation
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.domain.timer.RunningTimer
import com.jvcs.tracky.features.project.domain.timer.RunningTimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Keeps the platform notification in step with the running timer, and turns its two buttons back
 * into repository writes.
 *
 * Pause is not a state the database has - there is only start and stop - so pausing closes the
 * interval and banks the time, and the frozen card is held here in memory until Play opens a new
 * one. That is deliberate: a paused timer that did not survive process death would otherwise need
 * a column, a migration, and a new case in every query that asks what is running.
 */
class TimerNotificationCoordinator(
    private val runningTimerRepository: RunningTimerRepository,
    private val projectTaskRepository: ProjectTaskRepository,
    private val subTaskRepository: SubTaskRepository,
    private val controller: TimerNotificationController,
    private val startupReconciliation: StartupReconciliation,
    private val timeProvider: TimeProvider,
    private val applicationScope: CoroutineScope
) {
    private data class PausedTimer(val timer: RunningTimer, val elapsed: Duration, val asOf: Instant)

    private var started = false

    /** The timer the flow last reported, so Pause knows what to stop. */
    private var running: RunningTimer? = null

    /** Set by Pause, cleared once a new interval is open. What keeps the frozen card up. */
    private var paused: PausedTimer? = null

    /** Idempotent, like StrandedTimerReconciler.start - both are called from app start-up. */
    fun start() {
        if (started) return
        started = true
        applicationScope.launch { observe() }
    }

    internal suspend fun observe() {
        // The same gate the timer-start paths wait on. An interval still open at start-up may be
        // about to be parked, and showing it would offer Pause a row worth every hour since it
        // opened.
        startupReconciliation.awaitReconciled()
        runningTimerRepository.observeRunningTimer().collect { timer ->
            running = timer
            when {
                timer != null -> {
                    paused = null
                    controller.show(timer.toSession(timeProvider.nowInstant))
                }
                // Pause closed the interval itself. The card stays, frozen, or the user loses the
                // only way to resume without reopening the app.
                paused != null -> Unit
                else -> controller.dismiss()
            }
        }
    }

    suspend fun onPause() {
        val timer = running ?: return
        val now = timeProvider.nowInstant

        // Freeze and show before writing: stopping makes the flow emit null, and the collector has
        // to already be able to tell a pause from a stop.
        val frozen = PausedTimer(timer, timer.elapsedAt(now), now)
        paused = frozen
        controller.show(frozen.session)

        val subTask = timer.subTask
        if (subTask != null) {
            subTaskRepository.stopSubTask(subTask.id)
        } else {
            projectTaskRepository.stopProjectTask(timer.task.id)
        }
    }

    suspend fun onResume() {
        val timer = paused?.timer ?: return
        // No state cleared here on purpose: the write opens an interval, the flow emits it, and the
        // collector clears `paused`. A failed start therefore leaves the frozen card up.
        val subTask = timer.subTask
        if (subTask != null) {
            subTaskRepository.startSubTask(subTask.id)
        } else {
            projectTaskRepository.startProjectTask(timer.task.id)
        }
    }

    private fun RunningTimer.toSession(now: Instant) = TimerNotificationSession(
        project = project,
        useLightTextColor = useLightTextColor,
        task = task,
        subTask = subTask,
        elapsed = elapsedAt(now),
        asOf = now,
        isRunning = true
    )

    private val PausedTimer.session: TimerNotificationSession
        get() = timer.toSession(asOf).copy(elapsed = elapsed, isRunning = false)
}
