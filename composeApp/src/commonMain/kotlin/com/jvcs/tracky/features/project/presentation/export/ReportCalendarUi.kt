package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.runtime.Immutable
import com.jvcs.tracky.features.project.domain.export.ReportMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlin.time.Duration

internal const val DAYS_PER_WEEK = 7

/** One month card of the "Per day" heat map. */
@Immutable
data class ReportMonthUi(
    /** "August 2026". */
    val title: String,
    /** "01:42:48". */
    val total: String,
    /** Monday-first rows of [DAYS_PER_WEEK] cells; `null` pads the days before the 1st and after the last. */
    val weeks: List<List<ReportDayUi?>>,
)

@Immutable
data class ReportDayUi(
    /** "01". */
    val dayOfMonth: String,
    /** "1h34"; `null` when the day banked no time. */
    val duration: String?,
    /** The day's time against the busiest day of the whole report, 0..1. */
    val intensity: Float,
    /** The busiest day of the report, drawn solid. */
    val isPeak: Boolean,
)

/**
 * Maps [months] to calendar cards. Intensity is measured against the busiest day of all months,
 * not per month, so equal tints mean equal time on every card.
 */
internal fun List<ReportMonth>.toReportMonthUis(): List<ReportMonthUi> {
    val peak = maxOfOrNull { it.maxDayTotal } ?: Duration.ZERO
    return map { it.toReportMonthUi(peak) }
}

private fun ReportMonth.toReportMonthUi(peak: Duration): ReportMonthUi {
    val yearMonth = YearMonth(year, month)
    val days =
        (1..yearMonth.numberOfDays).map { day ->
            val total = dayTotals[LocalDate(year, month, day)]
            ReportDayUi(
                dayOfMonth = day.toString().padStart(2, '0'),
                duration = total?.let(::formatDayDuration),
                intensity = if (total != null && peak.isPositive()) (total / peak).toFloat() else 0f,
                isPeak = total != null && total == peak && peak.isPositive(),
            )
        }
    return ReportMonthUi(
        title = formatMonthTitle(yearMonth),
        total = formatReportDuration(total),
        weeks = days.toMondayFirstWeeks(leadingBlanks = yearMonth.firstDay.dayOfWeek.isoDayNumber - 1),
    )
}

/** Lays a month's days out in full weeks, padded with `null` before the 1st and after the last day. */
internal fun List<ReportDayUi>.toMondayFirstWeeks(leadingBlanks: Int): List<List<ReportDayUi?>> {
    val cells = List(leadingBlanks) { null } + this
    val trailingBlanks = (DAYS_PER_WEEK - cells.size % DAYS_PER_WEEK) % DAYS_PER_WEEK
    return (cells + List(trailingBlanks) { null }).chunked(DAYS_PER_WEEK)
}
