package com.jvcs.tracky.features.project.presentation.task_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.util.TimeManager
import com.jvcs.tracky.core.domain.util.TimerState
import com.jvcs.tracky.designsystem.util.formatDurationHoursMinutesSeconds
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.presentation.mappers.CountedInterval
import com.jvcs.tracky.features.project.presentation.mappers.END_OF_DAY
import com.jvcs.tracky.features.project.presentation.mappers.clockFormat
import com.jvcs.tracky.features.project.presentation.mappers.countedDayIntervals
import com.jvcs.tracky.features.project.presentation.mappers.toProjectTaskUi
import com.jvcs.tracky.features.project.presentation.mappers.toProjectUi
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.task_detail.model.DailyStatistic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
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
    private val subTaskRepository: SubTaskRepository,
    private val projectRepository: ProjectRepository,
    private val timeManager: TimeManager,
) : ViewModel() {

    private var hasLoadedInitialData = false
    private var loadedProjectId: String? = null

    private val _state = MutableStateFlow(TaskDetailState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    observeTask()
                    hasLoadedInitialData = true
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                TaskDetailState(),
            )

    /**
     * The task row, its subtasks and the live clock, derived together.
     *
     * The task row carries no subtasks (`TaskWithIntervals` has no such relation), so they come from
     * their own flow. Without them the duration is the task's own figure rather than the subtask sum
     * Project Detail shows, a running subtask never ticks here, and the daily table counts the
     * task's enclosing intervals on top of the subtasks'.
     *
     * One derivation rather than a tick collector patching what a row collector wrote: a row
     * emission while a timer runs would otherwise drop the clock back to the banked value until the
     * next tick.
     */
    private fun observeTask() {
        combine(
            projectTaskRepository.getProjectTaskWithIntervalsById(taskId).filterNotNull(),
            subTaskRepository.getSubTasksForTask(taskId),
            timeManager.taskStates,
        ) { task, subTasks, activeTimers ->
            val domainTask = task.copy(subTasks = subTasks)
            val taskUi = domainTask.toProjectTaskUi().withLiveTimer(activeTimers)
            _state.update {
                it.copy(
                    task = taskUi,
                    projectId = task.parentProjectId,
                    dailyStatistics = domainTask.toDailyStatistics(),
                    isTimerRunning = taskUi.isTimerRunning,
                )
            }
            loadProjectColors(task.parentProjectId)
        }.launchIn(viewModelScope)
    }

    /**
     * Applies TimeManager's live values, by the rule ProjectDetailViewModel.updateUiWithTimerValues
     * uses, so this card and the task card on Project Detail read the same figure.
     *
     * A task with subtasks is timed only through them: it runs while one of them runs and its
     * displayed duration is their sum, so it takes no tick of its own.
     */
    private fun ProjectTaskUi.withLiveTimer(activeTimers: Map<String, TimerState>): ProjectTaskUi {
        val liveSubTasks =
            subTasks.map { subTask ->
                val tick = activeTimers[subTask.projectSubTaskId]
                if (tick != null && tick.isRunning) {
                    subTask.copy(
                        durationMillis = tick.totalDuration.inWholeMilliseconds,
                        isTimerRunning = true,
                    )
                } else {
                    subTask.copy(isTimerRunning = false)
                }
            }
        if (liveSubTasks.isNotEmpty()) {
            return copy(subTasks = liveSubTasks, isTimerRunning = liveSubTasks.any { it.isTimerRunning })
        }
        val tick = activeTimers[projectTaskId]
        return if (tick != null && tick.isRunning) {
            copy(durationMillis = tick.totalDuration.inWholeMilliseconds, isTimerRunning = true)
        } else {
            copy(isTimerRunning = false)
        }
    }

    /**
     * The parent project's colours, for the header tint and the duration card. Read once per
     * project: the task flow re-emits on every timer tick and title edit, the colours do not change
     * from this screen.
     */
    private suspend fun loadProjectColors(projectId: String) {
        if (loadedProjectId == projectId) return
        loadedProjectId = projectId
        val project = projectRepository.getProjectById(projectId)?.toProjectUi() ?: return
        _state.update {
            it.copy(
                projectColor = project.color,
                useLightTextColor = project.useLightTextColor,
            )
        }
    }

    /**
     * This task's tracked time, one row per counted interval slice, newest first.
     *
     * Goes through [countedDayIntervals] rather than reading `intervals` directly, so this screen
     * agrees with the rest of the app:
     *
     * - A task that owns subtasks is counted through their intervals only; its own enclosing
     *   intervals would double-bill every stretch a subtask already claimed (rule 1).
     * - An interval crossing midnight is split into one row per local day, so no row can read
     *   75 hours (rule 3). A slice cut at midnight ends at [END_OF_DAY], as in the daily overview.
     *
     * Durations use `HH:mm:ss`, not [formatDuration]'s stopwatch `HH:mm:ss:cc`: a slice is bounded
     * by its day, and the daily overview renders the same figures the same way.
     */
    private fun ProjectTask.toDailyStatistics(): List<DailyStatistic> =
        countedDayIntervals(TimeZone.currentSystemDefault())
            .sortedWith(compareByDescending<CountedInterval> { it.date }.thenByDescending { it.start })
            .map { interval ->
                DailyStatistic(
                    intervalId = interval.intervalId,
                    formattedDate = interval.date.toString(),
                    formattedStartTime = interval.start.format(clockFormat),
                    formattedEndTime =
                        if (interval.endsAtMidnight) {
                            END_OF_DAY
                        } else {
                            interval.end.format(clockFormat)
                        },
                    formattedDuration = formatDurationHoursMinutesSeconds(interval.durationMillis.milliseconds),
                )
            }

    fun onAction(action: TaskDetailAction) {
        when (action) {
            TaskDetailAction.OnToggleTimer -> toggleTimer()

            // Nothing to save or revert here: the text is edited and saved on the edit-text screen,
            // so leaving edit mode either way only drops the outline.
            TaskDetailAction.OnEditModeClick -> _state.update { it.copy(isEditMode = true) }

            TaskDetailAction.OnCloseEditModeClick -> _state.update { it.copy(isEditMode = false) }

            else -> Unit
        }
    }

    /**
     * The same resolution ProjectDetailViewModel.onToggleTimer makes. A task with subtasks is never
     * timed directly: its button pauses the running subtask, or resumes the one used most recently,
     * else the first unfinished one. Opening a parent-only interval would put time on the task that
     * no subtask owns, and stopping the task would close the subtask's interval behind the subtask
     * repository's back.
     *
     * Resuming needs no seeding: the timer continues from the banked total (RunningTimer.elapsedAt).
     */
    private fun toggleTimer() {
        val task = _state.value.task ?: return
        viewModelScope.launch {
            if (task.subTasks.isNotEmpty()) {
                val running = task.subTasks.find { it.isTimerRunning }
                if (running != null) {
                    subTaskRepository.stopSubTask(running.projectSubTaskId)
                    return@launch
                }
                val lastStartedId = subTaskRepository.lastStartedSubTaskId(taskId)
                val target =
                    task.subTasks.find { it.projectSubTaskId == lastStartedId && !it.isFinished }
                        ?: task.subTasks.firstOrNull { !it.isFinished }
                        ?: return@launch
                subTaskRepository.startSubTask(target.projectSubTaskId)
            } else if (task.isTimerRunning) {
                projectTaskRepository.stopProjectTask(taskId)
            } else {
                projectTaskRepository.startProjectTask(taskId)
            }
        }
    }
}
