package com.jvcs.tracky.features.project.presentation.task_detail

import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.task_detail.model.DailyStatistic

data class TaskDetailState(
    val task: ProjectTaskUi? = null,
    val isLoading: Boolean = false,
    val titleText: String = "",
    val dailyStatistics: List<DailyStatistic> = emptyList(),
    val isTimerRunning: Boolean = false
)
