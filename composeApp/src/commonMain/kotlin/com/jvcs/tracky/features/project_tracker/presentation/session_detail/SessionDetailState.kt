package com.jvcs.tracky.features.project_tracker.presentation.session_detail

import com.jvcs.tracky.core.presentation.model.ProjectSessionUi
import com.jvcs.tracky.features.project_tracker.presentation.session_detail.model.DailyStatistic

data class SessionDetailState(
    val session: ProjectSessionUi? = null,
    val isLoading: Boolean = false,
    val titleText: String = "",
    val dailyStatistics: List<DailyStatistic> = emptyList(),
    val isTimerRunning: Boolean = false
)
