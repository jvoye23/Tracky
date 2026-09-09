package com.jvcs.tracky.features.project.presentation.mappers

import com.jvcs.tracky.design_system.util.formatDurationHoursMinutesSeconds
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.presentation.models.PerDayStripUi
import com.jvcs.tracky.features.project.presentation.models.PerDayUi
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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

/**
 * Builds the "Per day" activity strip: one entry per day from the project's first tracked day
 * through [today], including the days in between on which nothing was tracked.
 *
 * Pure — [today] and [timeZone] are passed in rather than read from the system, so the whole thing
 * is testable without a clock.
 *
 * Returns `null` when the project has no banked time at all. A strip of uniformly blank tiles says
 * nothing that the absence of the card does not say more clearly, so the screen simply omits it.
 */
@OptIn(ExperimentalTime::class)
fun Project.toPerDayStripUi(today: LocalDate, timeZone: TimeZone): PerDayStripUi? {
    val millisByDate = countedIntervals()
        .groupingBy { (start, _) -> start.toLocalDateTime(timeZone).date }
        .fold(0L) { total, (_, millis) -> total + millis }

    val totalMillis = millisByDate.values.sum()
    if (totalMillis <= 0L) return null

    val firstDay = millisByDate.keys.min()
    // Normally just `today`. Guarding against a tracked day in the future keeps the tiles and the
    // total describing the same set of days if a device clock ever runs backwards.
    val lastDay = maxOf(today, millisByDate.keys.max())

    val days = (0..firstDay.daysUntil(lastDay)).map { offset ->
        val date = firstDay.plus(offset, DateTimeUnit.DAY)
        val millis = millisByDate[date] ?: 0L
        PerDayUi(
            weekdayLabel = date.format(weekdayFormat),
            dateLabel = date.format(dateLabelFormat),
            formattedDuration = if (millis > 0L) {
                formatDurationHoursMinutesSeconds(millis.milliseconds)
            } else null,
            trackedMillis = millis
        )
    }

    val busiestDate = millisByDate.entries
        .filter { it.value > 0L }
        // Ties go to the earlier day: the strip reads left to right, so naming the first of two
        // equal days matches the one the eye lands on first.
        .sortedWith(compareByDescending<Map.Entry<LocalDate, Long>> { it.value }.thenBy { it.key })
        .first()
        .key

    return PerDayStripUi(
        days = days,
        busiestDayLabel = "${busiestDate.format(weekdayFormat)} ${busiestDate.format(dateLabelFormat)}"
    )
}

/**
 * The intervals whose time the strip counts, as (start, duration) pairs.
 *
 * Mirrors [com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi.displayDuration]: a
 * subtask interval always nests inside one of its parent task's intervals, so counting both would
 * bill the same stretch of time twice. A task that owns subtasks therefore contributes only their
 * intervals, and the strip's total agrees with the project total in the hero card.
 *
 * Open intervals are dropped — their elapsed time is not banked into `durationMillis` until the
 * timer stops, the same rule `TaskDetailViewModel.calculateDailyStatistics` applies.
 */
@OptIn(ExperimentalTime::class)
private fun Project.countedIntervals(): List<Pair<Instant, Long>> =
    projectTasks.orEmpty().flatMap { task ->
        val subTasks = task.subTasks.orEmpty()
        if (subTasks.isEmpty()) {
            task.intervals
                .filter { it.endDateTimeUtc != null }
                .map { it.startDateTimeUtc to it.durationMillis }
        } else {
            subTasks.flatMap { subTask ->
                subTask.subTaskIntervals
                    .filter { it.endDateTimeUtc != null }
                    .map { it.startDateTimeUtc to it.durationMillis }
            }
        }
    }
