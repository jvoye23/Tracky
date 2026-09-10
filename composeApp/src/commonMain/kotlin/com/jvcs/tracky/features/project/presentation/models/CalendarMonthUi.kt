package com.jvcs.tracky.features.project.presentation.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * One cell of the month grid.
 *
 * Carries an already-formatted [dayLabel] plus one raw number for the tint, the same split
 * [PerDayUi] uses: the composable formats nothing and decides nothing about time.
 */
data class CalendarDayUi(
    val date: LocalDate,
    /** "01" — zero-padded, so the grid's columns line up. */
    val dayLabel: String,
    /** 0 when nothing was tracked. Drives the heat tint and the activity dot. */
    val trackedMillis: Long,
    val isToday: Boolean,
    /**
     * False for the leading and trailing cells that only exist to pad the grid to whole weeks.
     * They render greyed and are not selectable.
     */
    val isInMonth: Boolean
)

/**
 * One page of the calendar: a whole month, padded to complete Monday-first weeks.
 *
 * [maxTrackedMillis] is the **project-wide** busiest day, not this month's. Normalising per month
 * would repaint the same day as the user swipes — a quiet month would light up as brightly as a
 * busy one — so the ramp's denominator has to be stable across every page.
 */
data class CalendarMonthUi(
    val yearMonth: YearMonth,
    /** "September 2026". */
    val monthLabel: String,
    /** "20:08" for the header's "this month" line; null when the month banked nothing. */
    val monthTotalLabel: String?,
    /**
     * Always 42 — six whole weeks, oldest first — so every page of the pager is the same height
     * and swiping does not shift the day list below it.
     */
    val days: List<CalendarDayUi>,
    /** "Tue 08" — the busiest day *in this month*; null when the month banked nothing. */
    val busiestDayLabel: String?,
    val maxTrackedMillis: Long
) {
    /** The cells that belong to the month itself, without the padding either side. */
    val monthDays: List<CalendarDayUi> get() = days.filter { it.isInMonth }
}
