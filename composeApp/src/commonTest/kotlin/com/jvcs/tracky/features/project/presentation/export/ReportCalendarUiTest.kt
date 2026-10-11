package com.jvcs.tracky.features.project.presentation.export

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jvcs.tracky.features.project.domain.export.SampleProjectFixture
import com.jvcs.tracky.features.project.domain.export.toProjectReport
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ReportCalendarUiTest {

    private val months =
        SampleProjectFixture
            .load()
            .toProjectReport(TimeZone.of("Europe/Berlin"), SampleProjectFixture.exportedAt)
            .toProjectReportUi()
            .months

    private val days get() = months.flatMap { month -> month.weeks.flatten().filterNotNull() }

    @Test
    fun dayDurationsRoundToTheNearestMinute() {
        assertThat(formatDayDuration(1.hours + 51.minutes + 58.seconds)).isEqualTo("1h52")
        assertThat(formatDayDuration(29.seconds)).isEqualTo("0h00")
        assertThat(formatDayDuration(12.hours + 5.minutes)).isEqualTo("12h05")
    }

    @Test
    fun monthTitlesAreSpelledOut() {
        assertThat(formatMonthTitle(YearMonth(2026, 9))).isEqualTo("September 2026")
    }

    @Test
    fun monthsMatchTheDesignsCards() {
        assertThat(months.map { it.title }).containsExactly("August 2026", "September 2026")
        assertThat(months.map { it.total }).containsExactly("01:42:48", "02:24:57")
        // 1 Aug 2026 is a Saturday and 1 Sept a Tuesday; weeks start on Monday.
        assertThat(months[0].weeks).hasSize(6)
        assertThat(months[0].weeks[0].map { it?.dayOfMonth }).containsExactly(null, null, null, null, null, "01", "02")
        assertThat(months[1].weeks.last().map { it?.dayOfMonth })
            .containsExactly("28", "29", "30", null, null, null, null)
        assertThat(months.flatMap { it.weeks }).each { it.hasSize(DAYS_PER_WEEK) }
    }

    @Test
    fun trackedDaysShowTheDesignsFigures() {
        val tracked = days.filter { it.duration != null }.map { "${it.dayOfMonth} ${it.duration}" }

        assertThat(tracked).containsExactly(
            "14 0h04",
            "16 0h01",
            "17 0h00",
            "24 0h00",
            "25 0h04",
            "26 1h34",
            "27 0h00",
            "02 0h00",
            "08 1h52",
            "10 0h02",
            "13 0h01",
            "17 0h24",
            "18 0h00",
            "19 0h01",
            "22 0h04",
        )
        assertThat(days.first { it.dayOfMonth == "01" }.duration).isNull()
    }

    @Test
    fun intensityIsMeasuredAgainstTheBusiestDayOfAllMonths() {
        val peak = days.single { it.isPeak }
        val august26 = months[0].weeks.flatten().first { it?.dayOfMonth == "26" }!!

        assertThat(peak.dayOfMonth to peak.intensity).isEqualTo("08" to 1f)
        assertThat(august26.intensity).isCloseTo(0.84f, 0.01f)
        assertThat(days.filter { it.duration == null }).each { it.transform { day -> day.intensity }.isEqualTo(0f) }
    }
}
