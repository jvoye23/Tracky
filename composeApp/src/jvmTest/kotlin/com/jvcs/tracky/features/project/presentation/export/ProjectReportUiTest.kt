package com.jvcs.tracky.features.project.presentation.export

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jvcs.tracky.designsystem.theme.reportDefaultAccent
import com.jvcs.tracky.features.project.domain.export.SampleProjectFixture
import com.jvcs.tracky.features.project.domain.export.toProjectReport
import com.jvcs.tracky.features.project.domain.models.ProjectStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.report_status_active
import tracky.composeapp.generated.resources.report_status_finished
import tracky.composeapp.generated.resources.report_status_open
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ProjectReportUiTest {

    private val report =
        SampleProjectFixture
            .load()
            .toProjectReport(TimeZone.of("Europe/Berlin"), SampleProjectFixture.exportedAt)

    @Test
    fun durationsRoundToTheNearestSecondAndHoursNeverWrap() {
        assertThat(formatReportDuration(8.hours + 46.minutes + 19.seconds + 600.milliseconds)).isEqualTo("08:46:20")
        assertThat(formatReportDuration(19.seconds + 499.milliseconds)).isEqualTo("00:00:19")
        assertThat(formatReportDuration(123.hours + 59.minutes + 59.seconds + 500.milliseconds)).isEqualTo("124:00:00")
    }

    @Test
    fun datesUseTheReportsMonthSpelling() {
        assertThat(formatReportDate(LocalDate(2026, 9, 2))).isEqualTo("2 Sept 2026")
        assertThat(formatReportDate(LocalDate(2026, 6, 30))).isEqualTo("30 June 2026")
        val day = LocalDate(2026, 8, 14)
        assertThat(formatReportDateRange(day, day)).isEqualTo("14 Aug 2026")
    }

    @Test
    fun sharesRoundToWholePercent() {
        assertThat(listOf(0.0015f, 0.0147f, 0.4983f, 1f).map(::formatSharePercent))
            .containsExactly("0%", "1%", "50%", "100%")
    }

    @Test
    fun statusLabels() {
        assertThat(ProjectStatus.ACTIVE.labelRes()).isEqualTo(Res.string.report_status_active)
        assertThat(taskStatusLabelRes(isFinished = true)).isEqualTo(Res.string.report_status_finished)
        assertThat(taskStatusLabelRes(isFinished = false)).isEqualTo(Res.string.report_status_open)
    }

    @Test
    fun sampleProjectMapsToTheDesignsFigures() {
        val ui = report.toProjectReportUi()

        assertThat(ui.colorHex).isEqualTo("#06D9E5")
        assertThat(ui.exportedDate).isEqualTo("22 Sept 2026")
        assertThat(ui.summary.totalTracked).isEqualTo("08:46:20")
        assertThat(ui.summary.activeDateRange).isEqualTo("14 Aug 2026 – 22 Sept 2026")
        assertThat(ui.taskRows.map { it.sharePercent }).containsExactly("50%", "4%", "0%", "44%", "1%")
        assertThat(ui.taskRows.map { it.duration })
            .containsExactly("04:21:39", "00:23:29", "00:00:48", "03:52:39", "00:07:44")
    }

    @Test
    fun aProjectWithoutColorOrActivityFallsBack() {
        val ui =
            report
                .copy(
                    colorArgb = null,
                    description = " ",
                    summary = report.summary.copy(firstActiveDate = null, lastActiveDate = null),
                ).toProjectReportUi()

        assertThat(ui.accent).isEqualTo(reportDefaultAccent)
        assertThat(ui.colorHex).isNull()
        assertThat(ui.description).isNull()
        assertThat(ui.summary.activeDateRange).isNull()
    }
}
