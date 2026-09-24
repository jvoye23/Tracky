package com.jvcs.tracky.features.project.presentation.mappers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
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

    private fun at(
        date: String,
        time: String,
        zone: TimeZone = TimeZone.UTC,
    ): Instant = LocalDateTime(LocalDate.parse(date), LocalTime.parse(time)).toInstant(zone)

    private fun hoursMillis(h: Double): Long = (h * 60 * 60 * 1000).toLong()

    @Test
    fun aSameDayIntervalIsOneSliceCarryingItsRealStartAndEnd() {
        val slices =
            splitAcrossLocalDays(
                startedAt = at("2026-09-09", "09:30"),
                endedAt = at("2026-09-09", "10:12"),
                totalDurationMillis = 42 * 60 * 1000L,
                timeZone = TimeZone.UTC,
            )

        assertThat(slices.size).isEqualTo(1)
        val slice = slices.single()
        assertThat(slice.date).isEqualTo(LocalDate(2026, 9, 9))
        assertThat(slice.start).isEqualTo(LocalTime(9, 30))
        assertThat(slice.end).isEqualTo(LocalTime(10, 12))
        assertThat(slice.endsAtMidnight).isFalse()
        assertThat(slice.durationMillis).isEqualTo(42 * 60 * 1000L)
        assertThat(slice.sliceIndex).isEqualTo(0)
        assertThat(slice.sliceCount).isEqualTo(1)
    }

    @Test
    fun anIntervalCrossingMidnightGivesEachDayItsOwnShare() {
        val slices =
            splitAcrossLocalDays(
                startedAt = at("2026-09-09", "23:40"),
                endedAt = at("2026-09-10", "00:20"),
                totalDurationMillis = 40 * 60 * 1000L,
                timeZone = TimeZone.UTC,
            )

        assertThat(slices.size).isEqualTo(2)
        assertThat(slices[0].date).isEqualTo(LocalDate(2026, 9, 9))
        assertThat(slices[0].start).isEqualTo(LocalTime(23, 40))
        assertThat(slices[0].durationMillis).isEqualTo(20 * 60 * 1000L)
        assertThat(slices[0].endsAtMidnight, name = "cut at the boundary, so it renders as 24:00").isTrue()

        assertThat(slices[1].date).isEqualTo(LocalDate(2026, 9, 10))
        assertThat(slices[1].start).isEqualTo(LocalTime(0, 0))
        assertThat(slices[1].end).isEqualTo(LocalTime(0, 20))
        assertThat(slices[1].durationMillis).isEqualTo(20 * 60 * 1000L)
        assertThat(slices[1].endsAtMidnight).isFalse()
    }

    @Test
    fun theReportedBugSpreadsAcrossItsFourDaysAndNoDayExceedsTwentyFourHours() {
        // 2026-09-09 14:57 -> 2026-09-12 18:18, the 75:21:06 that started this.
        val total = 271_266_120L
        val slices =
            splitAcrossLocalDays(
                startedAt = at("2026-09-09", "14:57"),
                endedAt = at("2026-09-12", "18:18:06.120"),
                totalDurationMillis = total,
                timeZone = TimeZone.UTC,
            )

        assertThat(slices.size).isEqualTo(4)
        assertThat(slices.sumOf { it.durationMillis }).isEqualTo(total)
        slices.forEach {
            assertThat(
                it.durationMillis <= 24 * 60 * 60 * 1000L,
                name = "no day can hold more than 24h, but ${it.date} got ${it.durationMillis}",
            ).isTrue()
        }
        assertThat(slices[0].durationMillis).isEqualTo(hoursMillis(9.05))
    }

    @Test
    fun sliceDurationsAlwaysSumToTheStoredTotal() {
        // A deliberately awkward total that does not divide evenly across three days.
        val total = 1_000_001L
        val slices =
            splitAcrossLocalDays(
                startedAt = at("2026-09-09", "23:00"),
                endedAt = at("2026-09-11", "01:00"),
                totalDurationMillis = total,
                timeZone = TimeZone.UTC,
            )

        assertThat(slices.size).isEqualTo(3)
        // The remainder lands on the last slice rather than being lost to integer division.
        assertThat(slices.sumOf { it.durationMillis }).isEqualTo(total)
    }

    @Test
    fun theStoredDurationIsApportionedNotRecomputedFromTheClock() {
        // An edited row: two hours of wall clock, but only one hour banked.
        val slices =
            splitAcrossLocalDays(
                startedAt = at("2026-09-09", "23:00"),
                endedAt = at("2026-09-10", "01:00"),
                totalDurationMillis = hoursMillis(1.0),
                timeZone = TimeZone.UTC,
            )

        assertThat(slices.size).isEqualTo(2)
        // Half the span each, so half the banked hour each - not an hour each.
        assertThat(slices[0].durationMillis).isEqualTo(hoursMillis(0.5))
        assertThat(slices[1].durationMillis).isEqualTo(hoursMillis(0.5))
    }

    @Test
    fun aZeroLengthIntervalStaysOnItsStartDay() {
        val slices =
            splitAcrossLocalDays(
                startedAt = at("2026-09-09", "12:00"),
                endedAt = at("2026-09-09", "12:00"),
                totalDurationMillis = 0L,
                timeZone = TimeZone.UTC,
            )

        assertThat(slices.size).isEqualTo(1)
        assertThat(slices.single().date).isEqualTo(LocalDate(2026, 9, 9))
        assertThat(slices.single().durationMillis).isEqualTo(0L)
    }

    @Test
    fun anIntervalEndingExactlyAtMidnightIsNotSplit() {
        val slices =
            splitAcrossLocalDays(
                startedAt = at("2026-09-09", "23:00"),
                endedAt = at("2026-09-10", "00:00"),
                totalDurationMillis = hoursMillis(1.0),
                timeZone = TimeZone.UTC,
            )

        // The next day gets nothing, so it earns no slice and no calendar tint.
        assertThat(slices.size).isEqualTo(1)
        assertThat(slices.single().date).isEqualTo(LocalDate(2026, 9, 9))
        assertThat(slices.single().durationMillis).isEqualTo(hoursMillis(1.0))
    }

    @Test
    fun theZoneDecidesWhereTheCutFalls() {
        // 22:30 UTC on the 9th is 00:30 on the 10th in Berlin, so the same instants split
        // differently - and in Berlin the interval has already crossed.
        val started = at("2026-09-09", "22:30")
        val ended = at("2026-09-09", "23:30")

        assertThat(splitAcrossLocalDays(started, ended, hoursMillis(1.0), TimeZone.UTC).size).isEqualTo(1)
        assertThat(splitAcrossLocalDays(started, ended, hoursMillis(1.0), berlin).size).isEqualTo(1)
        assertThat(
            splitAcrossLocalDays(started, ended, hoursMillis(1.0), berlin).single().date,
        ).isEqualTo(LocalDate(2026, 9, 10))
    }

    @Test
    fun aSpringForwardDayIsCutWhereItActuallyFalls() {
        // Europe/Berlin 2026-03-29: 02:00 jumps to 03:00, so that local day is 23 hours long.
        // Boundaries are instants, so the cut lands on the real midnight either way.
        val started = LocalDateTime(LocalDate(2026, 3, 28), LocalTime(23, 0)).toInstant(berlin)
        val ended = LocalDateTime(LocalDate(2026, 3, 30), LocalTime(1, 0)).toInstant(berlin)
        val total = (ended - started).inWholeMilliseconds

        val slices = splitAcrossLocalDays(started, ended, total, berlin)

        assertThat(slices.size).isEqualTo(3)
        assertThat(slices.sumOf { it.durationMillis }).isEqualTo(total)
        assertThat(slices[1].date).isEqualTo(LocalDate(2026, 3, 29))
        // The short day really is 23 hours, and the slice says so.
        assertThat(slices[1].durationMillis).isEqualTo(23 * 60 * 60 * 1000L)
    }
}
