package com.jvcs.tracky.features.project.domain.export

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.jvcs.tracky.features.project.domain.models.ProjectStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * The report built from the sample the PDF design was drawn from must reproduce the design's
 * figures. The design rounds durations to the nearest second, so [hms] does too.
 */
class SampleProjectReportTest {

    private val report =
        SampleProjectFixture
            .load()
            .toProjectReport(TimeZone.of("Europe/Berlin"), SampleProjectFixture.exportedAt)

    private fun Duration.hms(): String {
        val seconds = toDouble(DurationUnit.SECONDS).roundToLong()
        return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    }

    @Test
    fun headerAndSummaryMatchTheDesign() {
        assertThat(report.title).isEqualTo("API Testing update")
        assertThat(report.status).isEqualTo(ProjectStatus.ACTIVE)
        assertThat(report.exportedAt).isEqualTo(LocalDate(2026, 9, 22))
        with(report.summary) {
            assertThat(totalTracked.hms()).isEqualTo("08:46:20")
            assertThat(listOf(taskCount, finishedTaskCount, openTaskCount)).containsExactly(5, 2, 3)
            assertThat(intervalCount).isEqualTo(92)
            assertThat(activeDays).isEqualTo(15)
            assertThat(firstActiveDate).isEqualTo(LocalDate(2026, 8, 14))
            assertThat(lastActiveDate).isEqualTo(LocalDate(2026, 9, 22))
        }
    }

    @Test
    fun taskTableMatchesTheDesign() {
        val tasks = report.tasks

        assertThat(tasks.map { it.title })
            .containsExactly(
                "Testing Tasks Update",
                "Cross Device Test",
                "iPhone Task",
                "Task Number Four",
                "Neuer Task",
            )
        assertThat(tasks.map { it.duration.hms() })
            .containsExactly("04:21:39", "00:23:29", "00:00:48", "03:52:39", "00:07:44")
        assertThat(tasks.map { (it.shareFraction * 100).roundToLong() }).containsExactly(50L, 4L, 0L, 44L, 1L)
        assertThat(tasks.map { it.subtasks.size }).containsExactly(4, 3, 0, 0, 0)
        assertThat(tasks.map { it.intervals.size }).containsExactly(43, 24, 4, 10, 11)
        assertThat(
            tasks.map { it.endDate },
        ).containsExactly(null, null, LocalDate(2026, 8, 27), LocalDate(2026, 9, 8), null)
    }

    @Test
    fun monthsMatchTheDesign() {
        val (august, september) = report.months

        assertThat(report.months.map { it.month }).containsExactly(Month.AUGUST, Month.SEPTEMBER)
        assertThat(august.total.hms()).isEqualTo("01:42:48")
        assertThat(september.total.hms()).isEqualTo("02:24:57")
        assertThat(august.dayTotals.getValue(LocalDate(2026, 8, 26)).hms()).isEqualTo("01:33:44")
        assertThat(september.maxDayTotal.hms()).isEqualTo("01:51:59")
    }

    @Test
    fun taskSectionsMatchTheDesign() {
        val testing = report.tasks.first()
        val sharedInterval = testing.intervals.single { it.start.hour == 13 && it.start.minute == 21 }

        assertThat(testing.subtasks.map { it.duration.hms() })
            .containsExactly("05:30:48", "00:00:31", "00:00:00", "00:01:05")
        assertThat(testing.subtasks.map { it.intervalCount }).containsExactly(21, 6, 0, 3)
        assertThat(testing.intervals.first().date).isEqualTo(LocalDate(2026, 8, 14))
        assertThat(
            testing.intervals
                .first()
                .start
                .toSecondOfDay(),
        ).isEqualTo(LocalTime(6, 58, 4).toSecondOfDay())
        assertThat(sharedInterval.subtaskTitles).containsExactly("Subtask Number One", "subtask update")
        assertThat(sharedInterval.duration.hms()).isEqualTo("01:32:00")
    }
}
