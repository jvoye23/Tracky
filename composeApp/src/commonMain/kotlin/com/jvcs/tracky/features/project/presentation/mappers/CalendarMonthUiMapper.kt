package com.jvcs.tracky.features.project.presentation.mappers

import com.jvcs.tracky.design_system.util.formatDurationHoursMinutes
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.presentation.models.CalendarDayUi
import com.jvcs.tracky.features.project.presentation.models.CalendarMonthUi
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/** "September 2026" — the pager's page title. */
private val monthLabelFormat = LocalDate.Format {
    monthName(MonthNames.ENGLISH_FULL)
    chars(" ")
    year()
}

/** "Sep, 08, 2026" — the footer's busiest-day name. */
private val busiestDayFormat = LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    chars(", ")
    day()
    chars(", ")
    year()
}

/** "01" — zero-padded, so the grid's columns line up. */
private val dayLabelFormat = LocalDate.Format { day() }

/** The grid is Monday-first, matching the design's `M T W T F S S` header. */
private const val DAYS_PER_WEEK = 7

/**
 * Every page is six weeks, whether or not the month needs them.
 *
 * Six is the most any month can span — 31 days opening on a Sunday — so a fixed six always fits.
 * The alternative, sizing each page to its own month, makes the pager change height mid-swipe
 * and shoves the day list below it up and down as the user pages. A month that only needs five
 * weeks spends the sixth on trailing days of the next month, drawn the same muted way the
 * leading ones already are.
 */
private const val WEEKS_PER_PAGE = 6

private const val CELLS_PER_PAGE = WEEKS_PER_PAGE * DAYS_PER_WEEK

/**
 * Every page of the calendar, oldest first — one [CalendarMonthUi] per month from the project's
 * first month through the current one.
 *
 * Built in a single pass over the whole project rather than a month at a time. The task tree is
 * already in memory, so producing every page up front costs one traversal and means swiping the
 * pager never waits on a recomputation.
 *
 * The range always includes [today]'s month, even for a project that banked nothing, so the
 * screen's initial selection always has a page to land on; and it is bounded by the calendar
 * rather than by activity, so the year picker can never select a year with no page.
 *
 * Intensity is normalised against the **project-wide** busiest day, never the visible month's.
 * Per-month normalisation would repaint the same day as the user swipes, lighting a quiet month
 * as brightly as a busy one.
 *
 * Pure — [timeZone] and [today] are passed in rather than read from the system.
 */
@OptIn(ExperimentalTime::class)
fun Project.toCalendarMonthsUi(today: LocalDate, timeZone: TimeZone): List<CalendarMonthUi> {
    val dayTotals: Map<LocalDate, Long> = countedDayIntervals(timeZone)
        .groupingBy { it.date }
        .fold(0L) { total, interval -> total + interval.durationMillis }
        // A day whose intervals all came out zero-length banked nothing, so it gets no tint and
        // no dot. Its intervals still appear in the day list - that is the list's own decision.
        .filterValues { it > 0L }

    val maxTracked = dayTotals.values.maxOrNull() ?: 0L
    val startMonth = startDateTimeUtc.toLocalDateTime(timeZone).date.yearMonth
    val trackedMonths = dayTotals.keys.map { it.yearMonth }

    val first = minOf(startMonth, trackedMonths.minOrNull() ?: today.yearMonth, today.yearMonth)
    val last = maxOf(trackedMonths.maxOrNull() ?: today.yearMonth, today.yearMonth)

    return (first..last).map { month -> month.toCalendarMonthUi(dayTotals, maxTracked, today) }
}

private fun YearMonth.toCalendarMonthUi(
    dayTotals: Map<LocalDate, Long>,
    maxTracked: Long,
    today: LocalDate
): CalendarMonthUi {
    // Monday is 1, so a month opening on a Monday needs no padding at all.
    val leading = firstDay.dayOfWeek.isoDayNumber - 1
    val gridStart = firstDay.plus(-leading, DateTimeUnit.DAY)

    val days = (0 until CELLS_PER_PAGE).map { offset ->
        val date = gridStart.plus(offset, DateTimeUnit.DAY)
        CalendarDayUi(
            date = date,
            dayLabel = date.format(dayLabelFormat),
            // A padding cell shows no activity even when the neighbouring month tracked some:
            // its time belongs to that month's own page.
            trackedMillis = if (date.yearMonth == this) dayTotals[date] ?: 0L else 0L,
            isToday = date == today,
            isInMonth = date.yearMonth == this
        )
    }

    val monthTotal = days.sumOf { it.trackedMillis }
    // Ties go to the earlier day, which the eye reaches first - the same rule the strip applies.
    val busiest = days.filter { it.trackedMillis > 0L }.maxByOrNull { it.trackedMillis }

    return CalendarMonthUi(
        yearMonth = this,
        monthLabel = firstDay.format(monthLabelFormat),
        monthTotalLabel = monthTotal.takeIf { it > 0L }?.let { formatDurationHoursMinutes(it.milliseconds) },
        days = days,
        busiestDayLabel = busiest?.date?.format(busiestDayFormat),
        maxTrackedMillis = maxTracked
    )
}
