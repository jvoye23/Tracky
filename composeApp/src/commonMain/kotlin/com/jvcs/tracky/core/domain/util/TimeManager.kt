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
import kotlin.time.Duration
import kotlin.time.TimeSource

// Data class representing the UI state of a SINGLE session
data class TimerState(
    val isRunning: Boolean = false,
    val totalDuration: Duration = Duration.ZERO,
    val formattedTime: String = "00:00:00:00"
)


class TimeManager(
    private val scope: CoroutineScope
) {
    // Holds the public state for ALL sessions, keyed by ID
    private val _sessionStates = MutableStateFlow<Map<String, TimerState>>(emptyMap())
    val sessionStates = _sessionStates.asStateFlow()

    // Internal tracking for active jobs and start times
    private val jobs = mutableMapOf<String, Job>()
    private val startMarks = mutableMapOf<String, TimeSource.Monotonic.ValueTimeMark>()
    private val accumulatedDurations = mutableMapOf<String, Duration>()

    /**
     * Toggles the timer for a specific project/session ID.
     * Requires a CoroutineScope (usually viewModelScope) to launch the ticker.
     */
    fun toggleTimer(sessionId: String, initialDuration: Duration = Duration.ZERO) {
        val currentState = _sessionStates.value[sessionId] ?: TimerState()

        if (currentState.isRunning) {
            pauseTimer(sessionId)
        } else {
            startTimer(sessionId, initialDuration)
        }
    }

    /**
     * Returns a flow that emits ONLY when the state for [sessionId] changes.
     * If the session doesn't exist, it emits a default (empty) TimerState.
     */
    fun getSessionState(sessionId: String): Flow<TimerState> {
        return sessionStates
            .map { map -> map[sessionId] ?: TimerState() }
            .distinctUntilChanged() // CRITICAL: Only emit if THIS specific timer updates
    }

    private fun startTimer(sessionId: String, initialDuration: Duration) {
        // Prevent double-start
        if (jobs[sessionId]?.isActive == true) return

        // 1. Set the mark
        startMarks[sessionId] = TimeSource.Monotonic.markNow()

        accumulatedDurations[sessionId] = initialDuration

        // Update state immediately to "Running"
        updateState(sessionId) { it.copy(isRunning = true) }

        // 2. Launch the ticker job
        jobs[sessionId] = scope.launch {
            while (isActive) {
                val currentAccumulated = accumulatedDurations[sessionId] ?: Duration.ZERO
                val timeSinceStart = startMarks[sessionId]?.elapsedNow() ?: Duration.ZERO
                val total = currentAccumulated + timeSinceStart

                updateState(sessionId) {
                    it.copy(
                        totalDuration = total,
                        formattedTime = formatDuration(total)
                    )
                }
                delay(10) // 10ms for UI updates
            }
        }
    }
    private fun pauseTimer(sessionId: String) {
        // 1. Cancel the job
        jobs[sessionId]?.cancel()
        jobs.remove(sessionId)

        // 2. Bank the elapsed time
        val startMark = startMarks[sessionId]
        val currentAccumulated = accumulatedDurations[sessionId] ?: Duration.ZERO

        if (startMark != null) {
            accumulatedDurations[sessionId] = currentAccumulated + startMark.elapsedNow()
        }

        // 3. Clear the mark and update state
        startMarks.remove(sessionId)
        updateState(sessionId) { it.copy(isRunning = false) }
    }

    fun stopAndResetTimer(sessionId: String) {
        jobs[sessionId]?.cancel()
        jobs.remove(sessionId)
        startMarks.remove(sessionId)
        accumulatedDurations.remove(sessionId)

        // Reset state to default or remove it entirely
        updateState(sessionId) { TimerState() }
    }

    // Helper to safely update the StateFlow Map
    private fun updateState(sessionId: String, update: (TimerState) -> TimerState) {
        _sessionStates.update { currentMap ->
            val oldState = currentMap[sessionId] ?: TimerState()
            val newState = update(oldState)
            currentMap + (sessionId to newState)
        }
    }
}