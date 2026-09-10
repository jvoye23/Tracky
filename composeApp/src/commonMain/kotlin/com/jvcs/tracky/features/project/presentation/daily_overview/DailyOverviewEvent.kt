package com.jvcs.tracky.features.project.presentation.daily_overview

import com.jvcs.tracky.design_system.util.UiText

sealed interface DailyOverviewEvent {
    data class Error(val error: UiText) : DailyOverviewEvent
}
