package com.jvcs.tracky.features.project.presentation.mappers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.presentation.fakes.interval
import com.jvcs.tracky.features.project.presentation.fakes.project
import com.jvcs.tracky.features.project.presentation.fakes.subInterval
import com.jvcs.tracky.features.project.presentation.fakes.subTask
import com.jvcs.tracky.features.project.presentation.fakes.task
import com.jvcs.tracky.features.project.presentation.models.CalendarMonthUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test

/**
 * Pure: data in, data out, with `today` and the zone both passed explicitly.
 *
 * The fixture project starts 2026-08-01, and "today" is 2026-09-10 throughout unless a case says
 * otherwise. September 2026 opens on a Tuesday, which is what the design mock shows.
 */
class CalendarMonthUiMapperTest {

    private val today = LocalDate(2026, 9, 10)
    private val september = YearMonth(2026, 9)

    private fun months(
        vararg tasks: ProjectTask,
        on: LocalDate = today,
        zone: TimeZone = TimeZone.UTC,
    ) = project(tasks = tasks.toList()).toCalendarMonthsUi(today = on, timeZone = zone)

    private fun List<CalendarMonthUi>.september() = single { it.yearMonth == september }

    // --- the page range -----------------------------------------------------------------------

    @Test
    fun `an empty project still gets pages from its start month through today`() {
        val pages = months()

        assertThat(pages.map { it.yearMonth }).isEqualTo(listOf(YearMonth(2026, 8), september))
    }

    @Test
    fun `today's month always has a page, even for a project that banked nothing`() {
        val pages = months(on = LocalDate(2027, 3, 4))

        assertThat(pages.last().yearMonth).isEqualTo(YearMonth(2027, 3))
    }

    @Test
    fun `the range is bounded by the calendar, not by activity, so it has no gaps`() {
        val pages = months(task(intervals = listOf(interval("2026-11-02T09:00:00Z", minutes = 30))))

        // August through November inclusive - October has no activity but still gets a page,
        // otherwise the year picker could select a month the pager cannot show.
        assertThat(pages.size).isEqualTo(4)
        assertThat(pages[2].yearMonth).isEqualTo(YearMonth(2026, 10))
        assertThat(pages[2].monthTotalLabel).isNull()
    }

    // --- the grid -----------------------------------------------------------------------------

    @Test
    fun `the grid is padded to whole Monday-first weeks`() {
        val sep = months().september()

        // September 2026 opens on a Tuesday, so one leading cell (Mon 31 Aug) then its 30 days.
        assertThat(sep.days.size % 7).isEqualTo(0)
        assertThat(sep.days.first().date).isEqualTo(LocalDate(2026, 8, 31))
    }

    @Test
    fun `every page is six weeks, however few the month needs`() {
        // Otherwise the pager changes height mid-swipe and shoves the day list up and down.
        val sizes =
            months(
                task(intervals = listOf(interval("2027-02-01T09:00:00Z", minutes = 30))),
            ).map { it.days.size }

        assertThat(sizes.distinct()).isEqualTo(listOf(42))
    }

    @Test
    fun `the sixth week spills into the following month rather than being blank`() {
        val sep = months().september()

        // 35 cells would have ended on 4 Oct; the sixth week carries on to the 11th.
        assertThat(sep.days.size).isEqualTo(42)
        assertThat(sep.days.last().date).isEqualTo(LocalDate(2026, 10, 11))
        assertThat(sep.days.takeLast(7).none { it.isInMonth }).isTrue()
    }

    @Test
    fun `padding cells are marked out of month and the rest are not`() {
        val sep = months().september()

        assertThat(sep.monthDays.size).isEqualTo(30)
        assertThat(
            sep.days
                .first()
                .isInMonth
                .not(),
        ).isTrue()
        assertThat(
            sep.days
                .last()
                .isInMonth
                .not(),
        ).isTrue()
        assertThat(sep.monthDays.all { it.date.month == september.month }).isTrue()
    }

    @Test
    fun `a month opening on a Monday needs no leading padding`() {
        // June 2026 opens on a Monday.
        val june = months(on = LocalDate(2026, 6, 15)).single { it.yearMonth == YearMonth(2026, 6) }

        assertThat(june.days.first().date).isEqualTo(LocalDate(2026, 6, 1))
        assertThat(june.days.first().isInMonth).isTrue()
    }

    @Test
    fun `february in a leap year carries all 29 days`() {
        val feb = months(on = LocalDate(2028, 2, 10)).single { it.yearMonth == YearMonth(2028, 2) }

        assertThat(feb.monthDays.size).isEqualTo(29)
        assertThat(feb.monthDays.last().date).isEqualTo(LocalDate(2028, 2, 29))
    }

    @Test
    fun `day labels are zero padded`() {
        assertThat(
            months()
                .september()
                .monthDays
                .first()
                .dayLabel,
        ).isEqualTo("01")
        assertThat(
            months()
                .september()
                .monthDays
                .last()
                .dayLabel,
        ).isEqualTo("30")
    }

    @Test
    fun `today is marked on exactly one cell of its own month`() {
        val sep = months().september()

        assertThat(sep.monthDays.filter { it.isToday }.map { it.date }).isEqualTo(listOf(today))
    }

    // --- totals and tint ----------------------------------------------------------------------

    @Test
    fun `a day's tracked time lands on its cell and nowhere else`() {
        val sep = months(task(intervals = listOf(interval("2026-09-08T09:30:00Z", minutes = 42)))).september()

        assertThat(sep.monthDays.single { it.date == LocalDate(2026, 9, 8) }.trackedMillis).isEqualTo(42 * 60_000L)
        assertThat(sep.monthDays.single { it.date == LocalDate(2026, 9, 9) }.trackedMillis).isEqualTo(0L)
    }

    @Test
    fun `a padding cell shows no activity even when that day tracked time`() {
        // 31 Aug is September's leading padding cell; its time belongs to August's page.
        val pages = months(task(intervals = listOf(interval("2026-08-31T09:00:00Z", minutes = 25))))

        assertThat(
            pages
                .september()
                .days
                .first()
                .trackedMillis,
        ).isEqualTo(0L)
        assertThat(
            pages
                .first()
                .monthDays
                .single { it.date == LocalDate(2026, 8, 31) }
                .trackedMillis,
        ).isEqualTo(25 * 60_000L)
    }

    @Test
    fun `the month total sums only that month's days`() {
        val sep =
            months(
                task(
                    intervals =
                        listOf(
                            interval("2026-09-05T09:00:00Z", minutes = 60),
                            interval("2026-09-08T09:00:00Z", minutes = 42),
                        ),
                ),
            ).september()

        assertThat(sep.monthTotalLabel).isEqualTo("01:42")
    }

    @Test
    fun `a month that banked nothing has no total and no busiest day`() {
        val sep = months().september()

        assertThat(sep.monthTotalLabel).isNull()
        assertThat(sep.busiestDayLabel).isNull()
    }

    @Test
    fun `the busiest day names a day in that month, ties going to the earlier one`() {
        val sep =
            months(
                task(
                    intervals =
                        listOf(
                            interval("2026-09-08T09:00:00Z", minutes = 30),
                            interval("2026-09-15T09:00:00Z", minutes = 30),
                        ),
                ),
            ).september()

        assertThat(sep.busiestDayLabel).isEqualTo("Sep, 08, 2026")
    }

    @Test
    fun `intensity is normalised project-wide so a day keeps its colour across pages`() {
        val pages =
            months(
                task(
                    intervals =
                        listOf(
                            interval("2026-08-04T09:00:00Z", minutes = 240),
                            interval("2026-09-08T09:00:00Z", minutes = 30),
                        ),
                ),
            )

        // Every page shares one denominator - the busiest day in the whole project, not the month.
        assertThat(pages.map { it.maxTrackedMillis }).isEqualTo(listOf(240 * 60_000L, 240 * 60_000L))
    }

    // --- rules inherited from countedDayIntervals ---------------------------------------------

    @Test
    fun `a task with subtasks is not double counted`() {
        val sep =
            months(
                task(
                    intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 42)),
                    subTasks = listOf(subTask(intervals = listOf(subInterval("2026-09-08T09:00:00Z", minutes = 42)))),
                ),
            ).september()

        assertThat(sep.monthDays.single { it.date == LocalDate(2026, 9, 8) }.trackedMillis).isEqualTo(42 * 60_000L)
        assertThat(sep.monthTotalLabel).isEqualTo("00:42")
    }

    @Test
    fun `an open interval tints nothing`() {
        val sep =
            months(
                task(intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 30, open = true))),
            ).september()

        assertThat(sep.monthTotalLabel).isNull()
        assertThat(sep.maxTrackedMillis).isEqualTo(0L)
    }

    @Test
    fun `the zone decides which cell a day's time lands on`() {
        val tasks = arrayOf(task(intervals = listOf(interval("2026-09-05T02:00:00Z", minutes = 30))))

        assertThat(months(*tasks).september().busiestDayLabel).isEqualTo("Sep, 05, 2026")
        assertThat(
            months(*tasks, zone = TimeZone.of("America/New_York")).september().busiestDayLabel,
        ).isEqualTo("Sep, 04, 2026")
    }
}
