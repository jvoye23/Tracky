package com.jvcs.tracky.features.project.presentation.mappers

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The midnight cut.
 *
 * The invariant behind every case: the slices' durations sum **exactly** to the interval's stored
 * duration, and no slice claims more than the day it sits in can hold.
 */
@OptIn(ExperimentalTime::class)
internal class IntervalDaySplitterTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    private fun at(date: String, time: String, zone: TimeZone = TimeZone.UTC): Instant =
        LocalDateTime(LocalDate.parse(date), LocalTime.parse(time)).toInstant(zone)

    private fun hoursMillis(h: Double): Long = (h * 60 * 60 * 1000).toLong()

    @Test
    fun aSameDayIntervalIsOneSliceCarryingItsRealStartAndEnd() {
        val slices = splitAcrossLocalDays(
            startedAt = at("2026-09-09", "09:30"),
            endedAt = at("2026-09-09", "10:12"),
            totalDurationMillis = 42 * 60 * 1000L,
            timeZone = TimeZone.UTC
        )

        assertEquals(1, slices.size)
        val slice = slices.single()
        assertEquals(LocalDate(2026, 9, 9), slice.date)
        assertEquals(LocalTime(9, 30), slice.start)
        assertEquals(LocalTime(10, 12), slice.end)
        assertFalse(slice.endsAtMidnight)
        assertEquals(42 * 60 * 1000L, slice.durationMillis)
        assertEquals(0, slice.sliceIndex)
        assertEquals(1, slice.sliceCount)
    }

    @Test
    fun anIntervalCrossingMidnightGivesEachDayItsOwnShare() {
        val slices = splitAcrossLocalDays(
            startedAt = at("2026-09-09", "23:40"),
            endedAt = at("2026-09-10", "00:20"),
            totalDurationMillis = 40 * 60 * 1000L,
            timeZone = TimeZone.UTC
        )

        assertEquals(2, slices.size)
        assertEquals(LocalDate(2026, 9, 9), slices[0].date)
        assertEquals(LocalTime(23, 40), slices[0].start)
        assertEquals(20 * 60 * 1000L, slices[0].durationMillis)
        assertTrue(slices[0].endsAtMidnight, "cut at the boundary, so it renders as 24:00")

        assertEquals(LocalDate(2026, 9, 10), slices[1].date)
        assertEquals(LocalTime(0, 0), slices[1].start)
        assertEquals(LocalTime(0, 20), slices[1].end)
        assertEquals(20 * 60 * 1000L, slices[1].durationMillis)
        assertFalse(slices[1].endsAtMidnight)
    }

    @Test
    fun theReportedBugSpreadsAcrossItsFourDaysAndNoDayExceedsTwentyFourHours() {
        // 2026-09-09 14:57 -> 2026-09-12 18:18, the 75:21:06 that started this.
        val total = 271_266_120L
        val slices = splitAcrossLocalDays(
            startedAt = at("2026-09-09", "14:57"),
            endedAt = at("2026-09-12", "18:18:06.120"),
            totalDurationMillis = total,
            timeZone = TimeZone.UTC
        )

        assertEquals(4, slices.size)
        assertEquals(total, slices.sumOf { it.durationMillis })
        slices.forEach {
            assertTrue(
                it.durationMillis <= 24 * 60 * 60 * 1000L,
                "no day can hold more than 24h, but ${it.date} got ${it.durationMillis}"
            )
        }
        assertEquals(hoursMillis(9.05), slices[0].durationMillis)
    }

    @Test
    fun sliceDurationsAlwaysSumToTheStoredTotal() {
        // A deliberately awkward total that does not divide evenly across three days.
        val total = 1_000_001L
        val slices = splitAcrossLocalDays(
            startedAt = at("2026-09-09", "23:00"),
            endedAt = at("2026-09-11", "01:00"),
            totalDurationMillis = total,
            timeZone = TimeZone.UTC
        )

        assertEquals(3, slices.size)
        // The remainder lands on the last slice rather than being lost to integer division.
        assertEquals(total, slices.sumOf { it.durationMillis })
    }

    @Test
    fun theStoredDurationIsApportionedNotRecomputedFromTheClock() {
        // An edited row: two hours of wall clock, but only one hour banked.
        val slices = splitAcrossLocalDays(
            startedAt = at("2026-09-09", "23:00"),
            endedAt = at("2026-09-10", "01:00"),
            totalDurationMillis = hoursMillis(1.0),
            timeZone = TimeZone.UTC
        )

        assertEquals(2, slices.size)
        // Half the span each, so half the banked hour each - not an hour each.
        assertEquals(hoursMillis(0.5), slices[0].durationMillis)
        assertEquals(hoursMillis(0.5), slices[1].durationMillis)
    }

    @Test
    fun aZeroLengthIntervalStaysOnItsStartDay() {
        val slices = splitAcrossLocalDays(
            startedAt = at("2026-09-09", "12:00"),
            endedAt = at("2026-09-09", "12:00"),
            totalDurationMillis = 0L,
            timeZone = TimeZone.UTC
        )

        assertEquals(1, slices.size)
        assertEquals(LocalDate(2026, 9, 9), slices.single().date)
        assertEquals(0L, slices.single().durationMillis)
    }

    @Test
    fun anIntervalEndingExactlyAtMidnightIsNotSplit() {
        val slices = splitAcrossLocalDays(
            startedAt = at("2026-09-09", "23:00"),
            endedAt = at("2026-09-10", "00:00"),
            totalDurationMillis = hoursMillis(1.0),
            timeZone = TimeZone.UTC
        )

        // The next day gets nothing, so it earns no slice and no calendar tint.
        assertEquals(1, slices.size)
        assertEquals(LocalDate(2026, 9, 9), slices.single().date)
        assertEquals(hoursMillis(1.0), slices.single().durationMillis)
    }

    @Test
    fun theZoneDecidesWhereTheCutFalls() {
        // 22:30 UTC on the 9th is 00:30 on the 10th in Berlin, so the same instants split
        // differently - and in Berlin the interval has already crossed.
        val started = at("2026-09-09", "22:30")
        val ended = at("2026-09-09", "23:30")

        assertEquals(1, splitAcrossLocalDays(started, ended, hoursMillis(1.0), TimeZone.UTC).size)
        assertEquals(1, splitAcrossLocalDays(started, ended, hoursMillis(1.0), berlin).size)
        assertEquals(
            LocalDate(2026, 9, 10),
            splitAcrossLocalDays(started, ended, hoursMillis(1.0), berlin).single().date
        )
    }

    @Test
    fun aSpringForwardDayIsCutWhereItActuallyFalls() {
        // Europe/Berlin 2026-03-29: 02:00 jumps to 03:00, so that local day is 23 hours long.
        // Boundaries are instants, so the cut lands on the real midnight either way.
        val started = LocalDateTime(LocalDate(2026, 3, 28), LocalTime(23, 0)).toInstant(berlin)
        val ended = LocalDateTime(LocalDate(2026, 3, 30), LocalTime(1, 0)).toInstant(berlin)
        val total = (ended - started).inWholeMilliseconds

        val slices = splitAcrossLocalDays(started, ended, total, berlin)

        assertEquals(3, slices.size)
        assertEquals(total, slices.sumOf { it.durationMillis })
        assertEquals(LocalDate(2026, 3, 29), slices[1].date)
        // The short day really is 23 hours, and the slice says so.
        assertEquals(23 * 60 * 60 * 1000L, slices[1].durationMillis)
    }
}
