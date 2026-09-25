package com.jvcs.tracky.features.project.presentation.export

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.jvcs.tracky.features.project.domain.export.SampleProjectFixture
import com.jvcs.tracky.features.project.domain.export.toProjectReport
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test

class ReportTaskSectionUiTest {

    private val sections =
        SampleProjectFixture
            .load()
            .toProjectReport(TimeZone.of("Europe/Berlin"), SampleProjectFixture.exportedAt)
            .toProjectReportUi()
            .taskSections

    @Test
    fun intervalDatesAndTimesUseTheDesignsSpelling() {
        assertThat(formatIntervalDate(LocalDate(2026, 9, 2))).isEqualTo("Wed 02 Sept")
        assertThat(formatClockRange(LocalTime(6, 58), LocalTime(7, 5, 59))).isEqualTo("06:58 – 07:05")
    }

    @Test
    fun headingsShowPeriodAndDescription() {
        assertThat(sections.map { it.period }).containsExactly(
            "14 Aug 2026",
            "14 Aug 2026",
            "14 Aug 2026 – 27 Aug 2026",
            "16 Aug 2026 – 8 Sept 2026",
            "25 Aug 2026",
        )
        // Trimmed; a description that only repeats the title is left out, as the design does.
        assertThat(sections.map { it.description })
            .containsExactly("new description", "neue Beschreibung", null, null, null)
        assertThat(sections.map { it.intervalCount }).containsExactly(43, 24, 4, 10, 11)
    }

    @Test
    fun subtasksMatchTheDesign() {
        assertThat(sections[0].subtasks.map { "${it.title} ${it.intervalCount} ${it.duration}" }).containsExactly(
            "Subtask Number One 21 05:30:48",
            "subtask update 6 00:00:31",
            "New Test 0 00:00:00",
            "subtask number 4 3 00:01:05",
        )
    }

    @Test
    fun intervalRowsNameTheirSubtasksOrADash() {
        val rows = sections[0].intervals

        assertThat(rows.first()).isEqualTo(ReportIntervalRowUi("Fri 14 Aug", "06:58 – 06:58", "—", "00:00:54"))
        assertThat(rows.first { it.duration == "01:32:00" })
            .isEqualTo(
                ReportIntervalRowUi("Wed 26 Aug", "13:21 – 14:53", "Subtask Number One, subtask update", "01:32:00"),
            )
    }
}
