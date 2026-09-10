package com.jvcs.tracky.features.project.presentation.mappers

import com.jvcs.tracky.design_system.util.formatDurationHoursMinutesSeconds
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.presentation.models.PerDayStripUi
import com.jvcs.tracky.features.project.presentation.models.PerDayUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.Padding
import kotlin.time.Duration.Companion.milliseconds

/** "Sat" — the abbreviated weekday, used whole in both the tiles and the footer. */
private val weekdayFormat = LocalDate.Format {
    dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
}

/** "25.8" — zero-padded day, unpadded month, per [PerDayUi.dateLabel]. */
private val dateLabelFormat = LocalDate.Format {
    day()
    chars(".")
    monthNumber(Padding.NONE)
}

/** How many active days the strip shows. Days with no tracked time never take a tile. */
private const val MAX_ACTIVE_DAYS = 10

/**
 * Builds the "Per day" activity strip: one tile per *active* day — a day that banked time — for
 * the [MAX_ACTIVE_DAYS] most recent of them, oldest first.
 *
 * Days on which nothing was tracked are left out entirely, gaps between two active days included.
 * A project that runs for weeks would otherwise spend most of the strip on blank tiles, and the
 * days that actually carry time would sit off-screen. Today earns a tile only by being tracked.
 *
 * Pure — [timeZone] is passed in rather than read from the system, so the whole thing is testable
 * without a clock. Which local day an interval lands on is the only thing the zone decides.
 *
 * Returns `null` when the project has no banked time at all: with no active days there is nothing
 * to draw, and the screen simply omits the card.
 */
fun Project.toPerDayStripUi(timeZone: TimeZone): PerDayStripUi? {
    val activeDays = countedDayIntervals(timeZone)
        .groupingBy { it.date }
        .fold(0L) { total, interval -> total + interval.durationMillis }
        // A day whose intervals all came out zero-length banked nothing, so it is not active.
        .filterValues { it > 0L }
        .map { (date, millis) -> DayTotal(date, millis) }
        .sortedBy { it.date }
        .takeLast(MAX_ACTIVE_DAYS)

    if (activeDays.isEmpty()) return null

    val days = activeDays.map { (date, millis) ->
        PerDayUi(
            weekdayLabel = date.format(weekdayFormat),
            dateLabel = date.format(dateLabelFormat),
            formattedDuration = formatDurationHoursMinutesSeconds(millis.milliseconds),
            trackedMillis = millis
        )
    }

    // Over the visible window only: the footer names a day the eye can find in the strip, and that
    // day is the one rendered at full tint.
    val busiestDate = activeDays.minWith(busiestFirst).date

    return PerDayStripUi(
        days = days,
        busiestDayLabel = "${busiestDate.format(weekdayFormat)} ${busiestDate.format(dateLabelFormat)}"
    )
}

/** How much time one day collected. */
private data class DayTotal(
    val date: LocalDate,
    val millis: Long
)

/** Busiest first. Equal days go to the earlier one, which the eye reaches first. */
private val busiestFirst = compareByDescending<DayTotal> { it.millis }.thenBy { it.date }
