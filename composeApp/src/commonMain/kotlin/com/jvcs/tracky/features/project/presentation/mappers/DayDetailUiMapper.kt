package com.jvcs.tracky.features.project.presentation.mappers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import com.jvcs.tracky.design_system.util.formatDurationHoursMinutesSeconds
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.presentation.models.DayDetailUi
import com.jvcs.tracky.features.project.presentation.models.DayIntervalUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/** "Tue, Sep 08" — the day list's heading. */
private val dateLabelFormat =
    LocalDate.Format {
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
        chars(", ")
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        chars(" ")
        day()
    }

/** "Sep 8, 2026" — the calendar headline, matching Material 3's date-picker header. */
private val headlineFormat =
    LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        chars(" ")
        day(Padding.NONE)
        chars(", ")
        year()
    }

/** "09:30" — the ends of a time range. */
internal val clockFormat =
    LocalTime.Format {
        hour()
        chars(":")
        minute()
    }

/** En dash, per the design. Typographic, not translatable. */
private const val RANGE_SEPARATOR = " – "

/**
 * How a slice cut at the day boundary reads.
 *
 * `00:00` is the *next* day's midnight, so rendering it literally gives "23:40 – 00:00", which
 * reads like a session that ran backwards. `24:00` is the same instant named from this day's side.
 */
internal const val END_OF_DAY = "24:00"

/**
 * The interval list for one day, in the order it happened.
 *
 * One entry per interval rather than per task: the design numbers the cards 01, 02, 03 down the
 * day. Which intervals count is [countedDayIntervals]' decision, so a task owning subtasks
 * contributes its subtasks' intervals and the total here agrees with that day's calendar cell.
 *
 * An interval that ran past midnight appears on both days, each showing only that day's share —
 * so this list, like every other per-day view, can never total more than 24 hours.
 *
 * Zero-length intervals are listed. They tint no calendar cell — a day that banked nothing is not
 * "active" — but the day did record them, and a list that silently dropped rows would be lying
 * about what happened.
 *
 * A day with nothing tracked yields an empty list and a zero total rather than null: the screen
 * renders an empty state, keeping the heading and its date in place.
 *
 * Pure — [timeZone] is passed in rather than read from the system.
 */
@OptIn(ExperimentalTime::class)
fun Project.toDayDetailUi(date: LocalDate, timeZone: TimeZone): DayDetailUi {
    val intervals =
        countedDayIntervals(timeZone)
            .filter { it.date == date }
            // Sorted before the cards are numbered, so 01 is genuinely the day's first interval.
            .sortedWith(compareBy({ it.start }, { it.intervalId }))

    return DayDetailUi(
        dateLabel = date.format(dateLabelFormat),
        headlineLabel = date.format(headlineFormat),
        totalDuration = formatDurationHoursMinutesSeconds(intervals.sumOf { it.durationMillis }.milliseconds),
        intervals =
            intervals.mapIndexed { index, interval ->
                DayIntervalUi(
                    intervalId = interval.intervalId,
                    indexLabel = (index + 1).toString().padStart(2, '0'),
                    taskTitle = interval.taskTitle,
                    subTaskTitle = interval.subTaskTitle,
                    timeRangeLabel =
                        interval.start.format(clockFormat) +
                            RANGE_SEPARATOR +
                            if (interval.endsAtMidnight) END_OF_DAY else interval.end.format(clockFormat),
                    formattedDuration = formatDurationHoursMinutesSeconds(interval.durationMillis.milliseconds),
                    projectColor = if (this.colorArgb != null) Color(colorArgb) else Color(0xFF475D92),
                )
            },
        // Distinct parent tasks: two intervals of the same task, or of two of its subtasks, are
        // one task's worth of work. "4 intervals · 4 tasks" counts different things on purpose.
        taskCount = intervals.map { it.taskId }.distinct().size,
    )
}
