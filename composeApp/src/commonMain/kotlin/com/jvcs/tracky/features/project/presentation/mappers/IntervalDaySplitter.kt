package com.jvcs.tracky.features.project.presentation.mappers

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Cuts an interval at every local midnight it crosses.
 *
 * Without this a day total can exceed 24 hours: an interval used to count in full against the day
 * it started on, so one left running over a weekend put the whole weekend on Friday. Splitting
 * makes "a day holds at most 24 hours" true by construction rather than by hoping no session runs
 * long.
 *
 * Two things it deliberately does not do:
 *
 * - **It does not recompute durations from the clock.** The stored `durationMillis` is the truth —
 *   it can legitimately differ from `end - start` on a server-supplied row or one the user edited —
 *   so the total is *apportioned* by each slice's share of the wall clock, with the rounding
 *   remainder on the last slice. That keeps `sum(day totals) == the interval's duration`, which
 *   matters because the per-day strip and the task total sit next to each other on screen.
 * - **It does not do local-time arithmetic.** Boundaries are real instants
 *   ([atStartOfDayIn]), so a 23-hour or 25-hour DST day is cut where it actually falls.
 *
 * A same-day interval comes back as a single slice carrying its real start and end — byte for byte
 * what the old code produced, so the overwhelmingly common case provably does not move.
 */
@OptIn(ExperimentalTime::class)
internal fun splitAcrossLocalDays(
    startedAt: Instant,
    endedAt: Instant,
    totalDurationMillis: Long,
    timeZone: TimeZone,
): List<DaySlice> {
    val startLocal = startedAt.toLocalDateTime(timeZone)
    val endLocal = endedAt.toLocalDateTime(timeZone)
    val spanMillis = (endedAt - startedAt).inWholeMilliseconds

    // A zero-length interval is kept, on its start day — the day recorded it, and dropping it would
    // be lying about what happened. A negative span is a clock that moved; same treatment, because
    // there is no honest way to spread it.
    if (spanMillis <= 0L || startLocal.date == endLocal.date) {
        return listOf(
            DaySlice(
                date = startLocal.date,
                start = startLocal.time,
                end = endLocal.time,
                endsAtMidnight = false,
                durationMillis = totalDurationMillis,
                sliceIndex = 0,
                sliceCount = 1,
            ),
        )
    }

    // Every midnight strictly inside the interval, as instants.
    val cuts =
        buildList {
            var day = startLocal.date
            while (true) {
                day = day.plus(1, DateTimeUnit.DAY)
                val midnight = day.atStartOfDayIn(timeZone)
                if (midnight >= endedAt) break
                add(midnight)
            }
        }

    val edges = listOf(startedAt) + cuts + listOf(endedAt)
    val sliceCount = edges.size - 1

    var apportioned = 0L
    return (0 until sliceCount).map { index ->
        val from = edges[index]
        val to = edges[index + 1]
        val fromLocal = from.toLocalDateTime(timeZone)
        val isLast = index == sliceCount - 1

        // The last slice takes whatever is left, so rounding can never lose or invent a millisecond.
        val duration =
            if (isLast) {
                totalDurationMillis - apportioned
            } else {
                val sliceSpan = (to - from).inWholeMilliseconds
                (totalDurationMillis * sliceSpan / spanMillis).also { apportioned += it }
            }

        DaySlice(
            date = fromLocal.date,
            start = fromLocal.time,
            end = to.toLocalDateTime(timeZone).time,
            endsAtMidnight = !isLast,
            durationMillis = duration,
            sliceIndex = index,
            sliceCount = sliceCount,
        )
    }
}

/**
 * One day's share of an interval.
 *
 * @param endsAtMidnight true when the slice was cut at the day boundary rather than by the interval
 *   ending. [end] then reads `00:00` — the next day's midnight — which renders as `24:00`, because
 *   "23:40 – 00:00" reads like a session that ran backwards.
 * @param sliceIndex 0-based position within the interval; `0` of `1` for an ordinary same-day one.
 */
internal data class DaySlice(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val endsAtMidnight: Boolean,
    val durationMillis: Long,
    val sliceIndex: Int,
    val sliceCount: Int,
)
