package com.jvcs.tracky.features.project_tracker.presentation.session_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.model.ProjectSession
import com.jvcs.tracky.core.domain.model.SessionInterval
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.core.presentation.mapper.toProjectSessionUi
import com.jvcs.tracky.design_system.util.formatDuration
import com.jvcs.tracky.design_system.util.parseDuration
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import com.jvcs.tracky.features.project_tracker.presentation.session_detail.model.DailyStatistic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds

class SessionDetailViewModel(
    private val sessionId: String,
    private val projectRepository: ProjectRepository,
    private val timeManager: TimeManager
) : ViewModel() {

    private val _state = MutableStateFlow(SessionDetailState())
    val state = _state
        .onStart {
            loadSession()
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SessionDetailState()
        )

    init {
        viewModelScope.launch {
            timeManager.sessionStates.collect { activeTimersMap ->
                val timerState = activeTimersMap[sessionId]
                if (timerState != null && timerState.isRunning) {
                    _state.update { currentState ->
                        currentState.copy(
                            session = currentState.session?.copy(
                                formattedDuration = timerState.formattedTime
                            )
                        )
                    }
                }
            }
        }
    }

    private fun loadSession() {
        viewModelScope.launch {
            projectRepository.getSessionWithIntervalsById(sessionId).collect { session ->
                session?.let {
                    _state.update { currentState ->
                        currentState.copy(
                            session = it.toProjectSessionUi(),
                            titleText = it.title,
                            dailyStatistics = calculateDailyStatistics(it.intervals),
                            isTimerRunning = it.isTimerRunning
                        )
                    }
                }
            }
        }
    }

    private fun calculateDailyStatistics(intervals: List<SessionInterval>): List<DailyStatistic> {
        return intervals
            .filter { it.endDateTimeUtc != null }
            .groupBy { it.startDateTimeUtc.toLocalDateTime(TimeZone.currentSystemDefault()).date }
            .map { (date, dayIntervals) ->
                val totalDurationMillis = dayIntervals.sumOf { it.durationMillis }
                DailyStatistic(
                    formattedDate = date.toString(),
                    formattedDuration = formatDuration(totalDurationMillis.milliseconds)
                )
            }
            .sortedByDescending { it.formattedDate }
    }

    fun onAction(action: SessionDetailAction) {
        when (action) {
            SessionDetailAction.OnToggleTimer -> toggleTimer()
            is SessionDetailAction.OnTitleChanged -> {
                _state.update { it.copy(titleText = action.newTitle) }
            }
            SessionDetailAction.OnSaveTitle -> saveTitle()
            else -> Unit
        }
    }

    private fun toggleTimer() {
        viewModelScope.launch {
            val isRunning = _state.value.isTimerRunning
            if (isRunning) {
                projectRepository.stopSession(sessionId)
                timeManager.stopAndResetTimer(sessionId)
            } else {
                projectRepository.startSession(sessionId)
                val currentDurationString = _state.value.session?.formattedDuration ?: "00:00:00"
                val currentDuration = parseDuration(currentDurationString)
                timeManager.toggleTimer(sessionId, currentDuration)
            }
        }
    }

    private fun saveTitle() {
        viewModelScope.launch {
            projectRepository.updateSessionTitle(sessionId, _state.value.titleText)
        }
    }
}
