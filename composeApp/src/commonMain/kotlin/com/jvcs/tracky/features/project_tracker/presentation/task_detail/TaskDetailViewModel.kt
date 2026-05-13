package com.jvcs.tracky.features.project_tracker.presentation.task_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.model.TaskInterval
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.core.presentation.mapper.toProjectTaskUi
import com.jvcs.tracky.design_system.util.formatDuration
import com.jvcs.tracky.design_system.util.parseDuration
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import com.jvcs.tracky.features.project_tracker.presentation.task_detail.model.DailyStatistic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class TaskDetailViewModel(
    private val taskId: String,
    private val projectRepository: ProjectRepository,
    private val timeManager: TimeManager
) : ViewModel() {

    private val _state = MutableStateFlow(TaskDetailState())
    val state = _state
        .onStart {
            loadSession()
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TaskDetailState()
        )

    init {
        viewModelScope.launch {
            timeManager.taskStates.collect { activeTimersMap ->
                val timerState = activeTimersMap[taskId]
                if (timerState != null && timerState.isRunning) {
                    _state.update { currentState ->
                        currentState.copy(
                            task = currentState.task?.copy(
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
            projectRepository.getTaskWithIntervalsById(taskId).collect { session ->
                session?.let {
                    _state.update { currentState ->
                        currentState.copy(
                            task = it.toProjectTaskUi(),
                            titleText = it.title,
                            dailyStatistics = calculateDailyStatistics(it.intervals),
                            isTimerRunning = it.isTimerRunning
                        )
                    }
                }
            }
        }
    }

    private fun calculateDailyStatistics(intervals: List<TaskInterval>): List<DailyStatistic> {
        return intervals
            .filter { it.endDateTimeUtc != null }
            .groupBy { Instant.parse(it.startDateTimeUtc).toLocalDateTime(TimeZone.currentSystemDefault()).date }
            .map { (date, dayIntervals) ->
                val totalDurationMillis = dayIntervals.sumOf { it.durationMillis }
                DailyStatistic(
                    formattedDate = date.toString(),
                    formattedDuration = formatDuration(totalDurationMillis.milliseconds)
                )
            }
            .sortedByDescending { it.formattedDate }
    }

    fun onAction(action: TaskDetailAction) {
        when (action) {
            TaskDetailAction.OnToggleTimer -> toggleTimer()
            is TaskDetailAction.OnTitleChanged -> {
                _state.update { it.copy(titleText = action.newTitle) }
            }
            TaskDetailAction.OnSaveTitle -> saveTitle()
            else -> Unit
        }
    }

    private fun toggleTimer() {
        viewModelScope.launch {
            val isRunning = _state.value.isTimerRunning
            if (isRunning) {
                projectRepository.stopTask(taskId)
                timeManager.stopAndResetTimer(taskId)
            } else {
                projectRepository.startTask(taskId)
                val currentDurationString = _state.value.task?.formattedDuration ?: "00:00:00"
                val currentDuration = parseDuration(currentDurationString)
                timeManager.toggleTimer(taskId, currentDuration)
            }
        }
    }

    private fun saveTitle() {
        viewModelScope.launch {
            projectRepository.updateTaskTitle(taskId, _state.value.titleText)
        }
    }
}
