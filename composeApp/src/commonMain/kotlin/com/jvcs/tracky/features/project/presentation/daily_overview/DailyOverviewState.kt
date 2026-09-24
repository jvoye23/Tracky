package com.jvcs.tracky.features.project.presentation.daily_overview

import androidx.compose.ui.graphics.Color
import com.jvcs.tracky.features.project.presentation.models.CalendarMonthUi
import com.jvcs.tracky.features.project.presentation.models.DayDetailUi
import kotlinx.datetime.LocalDate

data class DailyOverviewState(
    val projectTitle: String? = null,
    // Null until the project loads; the screen falls back to the default project colour.
    val projectColor: Color? = null,
    val selectedDate: LocalDate? = null,
    // Every page the calendar can show, built once when the project loads.
    val months: List<CalendarMonthUi> = emptyList(),
    val visibleMonthIndex: Int = 0,
    val dayDetail: DayDetailUi? = null,
    val isLoading: Boolean = true,
)
