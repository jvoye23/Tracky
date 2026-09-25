package com.jvcs.tracky.features.project.presentation.export

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.DurationUnit

private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L
private const val PERCENT = 100

/** The report's own spelling — "Sept" and "June" rather than kotlinx's three-letter "Sep" and "Jun". */
private val ReportMonthNames =
    MonthNames(
        listOf("Jan", "Feb", "Mar", "Apr", "May", "June", "July", "Aug", "Sept", "Oct", "Nov", "Dec"),
    )

private val ReportDateFormat =
    LocalDate.Format {
        day(Padding.NONE)
        char(' ')
        monthName(ReportMonthNames)
        char(' ')
        year()
    }

/**
 * "HH:MM:SS", rounded to the nearest second — unlike the app's truncating formatters, because a
 * report's rows must add up to its total: truncating each row loses up to a second apiece.
 * Hours are never wrapped and grow past two digits when they have to.
 */
internal fun formatReportDuration(duration: Duration): String {
    val totalSeconds = duration.toDouble(DurationUnit.SECONDS).roundToLong().coerceAtLeast(0)
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return listOf(hours, minutes, seconds).joinToString(":") { it.toString().padStart(2, '0') }
}

/** "22 Sept 2026". */
internal fun formatReportDate(date: LocalDate): String = ReportDateFormat.format(date)

/** "14 Aug 2026 – 22 Sept 2026", a single date when both ends fall on one day. */
internal fun formatReportDateRange(first: LocalDate, last: LocalDate): String =
    if (first == last) formatReportDate(first) else "${formatReportDate(first)} – ${formatReportDate(last)}"

/** "44%", rounded to the nearest percent, so a sliver of tracked time reads "0%". */
internal fun formatSharePercent(fraction: Float): String = "${(fraction * PERCENT).roundToInt()}%"
