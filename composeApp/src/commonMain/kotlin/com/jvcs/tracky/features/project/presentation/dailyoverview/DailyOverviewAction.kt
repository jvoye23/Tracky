package com.jvcs.tracky.features.project.presentation.dailyoverview

import kotlinx.datetime.LocalDate

sealed interface DailyOverviewAction {

    data object OnBackClick : DailyOverviewAction

    data class OnDateSelected(val date: LocalDate) : DailyOverviewAction

    /** The pager settled, a chevron was tapped, or a year was picked — all land as an index. */
    data class OnMonthChanged(val index: Int) : DailyOverviewAction
}
