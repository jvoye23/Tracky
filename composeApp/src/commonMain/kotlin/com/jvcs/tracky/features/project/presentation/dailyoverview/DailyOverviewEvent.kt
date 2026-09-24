package com.jvcs.tracky.features.project.presentation.dailyoverview

import com.jvcs.tracky.designsystem.util.UiText

sealed interface DailyOverviewEvent {

    data class Error(val error: UiText) : DailyOverviewEvent
}
