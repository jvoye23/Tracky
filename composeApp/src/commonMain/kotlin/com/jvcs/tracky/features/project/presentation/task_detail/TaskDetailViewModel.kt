package com.jvcs.tracky.features.project.presentation.task_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.features.project.presentation.mappers.CountedInterval
import com.jvcs.tracky.features.project.presentation.mappers.END_OF_DAY
import com.jvcs.tracky.features.project.presentation.mappers.clockFormat
import com.jvcs.tracky.features.project.presentation.mappers.countedDayIntervals
import com.jvcs.tracky.features.project.presentation.mappers.toProjectTaskUi
import com.jvcs.tracky.design_system.util.formatDurationHoursMinutesSeconds
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.presentation.task_detail.model.DailyStatistic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlin.time.Duration.Companion.milliseconds

class TaskDetailViewModel(
    private val taskId: String,
    private val projectTaskRepository: ProjectTaskRepository,
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
                                durationMillis = timerState.totalDuration.inWholeMilliseconds
                            )
                        )
                    }
                }
            }
        }
    }

    private fun loadSession() {
        viewModelScope.launch {
            projectTaskRepository.getProjectTaskWithIntervalsById(taskId).collect { session ->
                session?.let {
                    _state.update { currentState ->
                        currentState.copy(
                            task = it.toProjectTaskUi(),
                            titleText = it.title,
                            dailyStatistics = it.toDailyStatistics(),
                            isTimerRunning = it.isTimerRunning
                        )
                    }
                }
            }
        }
    }

    /**
     * This task's tracked time, one row per counted interval slice, newest first.
     *
     * Goes through [countedDayIntervals] rather than folding `intervals` directly, which is what it
     * used to do and what made this screen disagree with the rest of the app:
     *
     * - It counted a task's own intervals even when the task owns subtasks, double-billing every
     *   stretch that a subtask had already claimed (rule 1).
     * - It billed a multi-day interval entirely to the day it started on, which is how one day came
     *   to read 75 hours (rule 3). A slice cut at midnight ends at [END_OF_DAY], as in the daily
     *   overview, so a stretch running into the next day does not read as one that ran backwards.
     *
     * The formatter changes for the same reason. [formatDuration]'s `HH:mm:ss:cc` is a stopwatch
     * reading with an unbounded hours field - it is what let `75:21:06:12` render at all - while a
     * slice is bounded by its day and belongs in the same `HH:mm:ss` the daily overview uses.
     */
    private fun ProjectTask.toDailyStatistics(): List<DailyStatistic> =
        countedDayIntervals(TimeZone.currentSystemDefault())
            .sortedWith(compareByDescending<CountedInterval> { it.date }.thenByDescending { it.start })
            .map { interval ->
                DailyStatistic(
                    intervalId = interval.intervalId,
                    formattedDate = interval.date.toString(),
                    formattedStartTime = interval.start.format(clockFormat),
                    formattedEndTime = if (interval.endsAtMidnight) END_OF_DAY
                        else interval.end.format(clockFormat),
                    formattedDuration = formatDurationHoursMinutesSeconds(interval.durationMillis.milliseconds)
                )
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
            // isTimerRunning comes from the row this screen observes, and so does the clock, so
            // there is nothing to seed and nothing to stop beyond closing the interval.
            if (_state.value.isTimerRunning) {
                projectTaskRepository.stopProjectTask(taskId)
            } else {
                projectTaskRepository.startProjectTask(taskId)
            }
        }
    }

    private fun saveTitle() {
        viewModelScope.launch {
            projectTaskRepository.updateProjectTaskTitle(taskId, _state.value.titleText)
        }
    }
}
