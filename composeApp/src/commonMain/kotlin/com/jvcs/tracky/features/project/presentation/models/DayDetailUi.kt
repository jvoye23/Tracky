package com.jvcs.tracky.features.project.presentation.models

import androidx.compose.ui.graphics.Color

/**
 * One card in the day list: a single interval, exactly as the design draws it.
 *
 * Every field is already formatted. The card composable decides nothing about time.
 */
data class DayIntervalUi(
    val intervalId: String,
    /** "01" — the card's running number, assigned after the day is sorted. */
    val indexLabel: String,
    val taskTitle: String,
    /** The subtask that owns this interval, or `null` when the task timed it directly. */
    val subTaskTitle: String?,
    /** "09:30 – 10:12". Reads backwards for an interval that ran past midnight. */
    val timeRangeLabel: String,
    /** "00:42:11" — hours, minutes and seconds, matching the totals above it. */
    val formattedDuration: String,
    val projectColor: Color,
)

/**
 * Everything below the calendar for the selected day.
 *
 * A day with nothing tracked still produces a value — with an empty [intervals] list and a zero
 * total — because the screen shows an empty state rather than dropping the section entirely.
 */
data class DayDetailUi(
    /** "Tue, Sep 08" — the day list's own heading. */
    val dateLabel: String,
    /**
     * "Sep 8, 2026" — the calendar card's headline, which follows Material 3's date-picker
     * header. A longer form than [dateLabel]: it carries the year, because the calendar can be
     * paged years away from today.
     */
    val headlineLabel: String,
    /** "03:26:58". */
    val totalDuration: String,
    val intervals: List<DayIntervalUi>,
    /** Distinct parent tasks represented, not distinct intervals and not subtasks. */
    val taskCount: Int,
) {
    val intervalCount: Int get() = intervals.size

    val isEmpty: Boolean get() = intervals.isEmpty()
}
