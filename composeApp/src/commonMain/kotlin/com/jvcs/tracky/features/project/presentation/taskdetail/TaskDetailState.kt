package com.jvcs.tracky.features.project.presentation.taskdetail

import androidx.compose.ui.graphics.Color
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.taskdetail.model.DailyStatistic

data class TaskDetailState(
    val task: ProjectTaskUi? = null,
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val projectId: String? = null,
    val projectColor: Color? = null,
    val useLightTextColor: Boolean = false,
    val dailyStatistics: List<DailyStatistic> = emptyList(),
    val isTimerRunning: Boolean = false,
)
