package com.jvcs.tracky.core.domain.util

import com.jvcs.tracky.design_system.util.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.time.toDuration

// Data class representing the UI state of a SINGLE session
data class TimerState(
    val isRunning: Boolean = false,
    val totalDuration: Duration = Duration.ZERO,
    val formattedTime: String = "00:00:00:00"
)


class TimeManager(
    private val scope: CoroutineScope
) {
    // Holds the public state for ALL tasks, keyed by ID
    private val _taskStates = MutableStateFlow<Map<String, TimerState>>(emptyMap())
    val taskStates = _taskStates.asStateFlow()

    // Internal tracking for active jobs and start times
    private val jobs = mutableMapOf<String, Job>()
    private val startMarks = mutableMapOf<String, TimeSource.Monotonic.ValueTimeMark>()
    private val accumulatedDurations = mutableMapOf<String, Duration>()

    /**
     * Toggles the timer for a specific project/task ID.
     * Requires a CoroutineScope (usually viewModelScope) to launch the ticker.
     */
    fun toggleTimer(taskId: String, initialDuration: Duration = Duration.ZERO) {
        val currentState = _taskStates.value[taskId] ?: TimerState()

        if (currentState.isRunning) {
            pauseTimer(taskId)
        } else {
            startTimer(taskId, initialDuration)
        }
    }

    /**
     * Returns a flow that emits ONLY when the state for [taskId] changes.
     * If the session doesn't exist, it emits a default (empty) TimerState.
     */
    fun getTaskState(taskId: String): Flow<TimerState> {
        return taskStates
            .map { map -> map[taskId] ?: TimerState() }
            .distinctUntilChanged() // CRITICAL: Only emit if THIS specific timer updates
    }

    private fun startTimer(taskId: String, initialDuration: Duration) {
        // Prevent double-start
        if (jobs[taskId]?.isActive == true) return

        // 1. Set the mark
        startMarks[taskId] = TimeSource.Monotonic.markNow()

        accumulatedDurations[taskId] = initialDuration

        // Update state immediately to "Running"
        updateState(taskId) { it.copy(isRunning = true) }

        // 2. Launch the ticker job
        jobs[taskId] = scope.launch {

            while (isActive) {
                delay(10) // 10ms for UI updates
                val currentAccumulated = accumulatedDurations[taskId] ?: Duration.ZERO
                val timeSinceStart = startMarks[taskId]?.elapsedNow() ?: Duration.ZERO
                val total = currentAccumulated + timeSinceStart

                updateState(taskId) {
                    it.copy(
                        totalDuration = total,
                        formattedTime = formatDuration(total)
                    )
                }

            }

        }
    }
    private fun pauseTimer(taskId: String) {
        // 1. Cancel the job
        jobs[taskId]?.cancel()
        jobs.remove(taskId)

        // 2. Bank the elapsed time
        val startMark = startMarks[taskId]
        val currentAccumulated = accumulatedDurations[taskId] ?: Duration.ZERO

        if (startMark != null) {
            accumulatedDurations[taskId] = currentAccumulated + startMark.elapsedNow()
        }

        // 3. Clear the mark and update state
        startMarks.remove(taskId)
        updateState(taskId) { it.copy(isRunning = false) }
    }

    fun stopAndResetTimer(taskId: String) {
        jobs[taskId]?.cancel()
        jobs.remove(taskId)
        startMarks.remove(taskId)
        accumulatedDurations.remove(taskId)

        // Reset state to default or remove it entirely
        updateState(taskId) { TimerState() }
    }

    // Helper to safely update the StateFlow Map
    private fun updateState(taskId: String, update: (TimerState) -> TimerState) {
        _taskStates.update { currentMap ->
            val oldState = currentMap[taskId] ?: TimerState()
            val newState = update(oldState)
            currentMap + (taskId to newState)
        }
    }
}