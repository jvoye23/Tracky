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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test

/** Pure: data in, data out, with the day and the zone both passed explicitly. */
class DayDetailUiMapperTest {

    private val sep8 = LocalDate(2026, 9, 8)

    private fun day(
        vararg tasks: ProjectTask,
        on: LocalDate = sep8,
        zone: TimeZone = TimeZone.UTC,
    ) = project(tasks = tasks.toList()).toDayDetailUi(date = on, timeZone = zone)

    // --- the empty day ------------------------------------------------------------------------

    @Test
    fun `a day with nothing tracked keeps its heading and reports zero`() {
        val detail = day()

        assertThat(detail.isEmpty).isTrue()
        assertThat(detail.dateLabel).isEqualTo("Tue, Sep 08")
        assertThat(detail.totalDuration).isEqualTo("00:00:00")
        assertThat(detail.taskCount).isEqualTo(0)
        assertThat(detail.intervalCount).isEqualTo(0)
    }

    @Test
    fun `the headline carries the year, which the day heading does not`() {
        val detail = day(on = LocalDate(2026, 10, 15))

        assertThat(detail.dateLabel).isEqualTo("Thu, Oct 15")
        // Unpadded day, and a year: the calendar can be paged years away from today.
        assertThat(detail.headlineLabel).isEqualTo("Oct 15, 2026")
        assertThat(day().headlineLabel).isEqualTo("Sep 8, 2026")
    }

    @Test
    fun `intervals from other days are left out`() {
        val detail = day(task(intervals = listOf(interval("2026-09-07T09:00:00Z", minutes = 30))))

        assertThat(detail.isEmpty).isTrue()
    }

    // --- the cards ----------------------------------------------------------------------------

    @Test
    fun `a card carries the task title, the range and the duration`() {
        val detail =
            day(
                task(title = "Design review", intervals = listOf(interval("2026-09-08T09:30:00Z", minutes = 42))),
            )

        val card = detail.intervals.single()
        assertThat(card.indexLabel).isEqualTo("01")
        assertThat(card.taskTitle).isEqualTo("Design review")
        assertThat(card.subTaskTitle).isNull()
        assertThat(card.timeRangeLabel).isEqualTo("09:30 – 10:12")
        assertThat(card.formattedDuration).isEqualTo("00:42:00")
    }

    @Test
    fun `a subtask interval names both the task and the subtask`() {
        val detail =
            day(
                task(
                    title = "Auth endpoints",
                    subTasks =
                        listOf(
                            subTask(
                                title = "Token refresh",
                                intervals = listOf(subInterval("2026-09-08T13:15:00Z", minutes = 92)),
                            ),
                        ),
                ),
            )

        val card = detail.intervals.single()
        assertThat(card.taskTitle).isEqualTo("Auth endpoints")
        assertThat(card.subTaskTitle).isEqualTo("Token refresh")
        assertThat(card.timeRangeLabel).isEqualTo("13:15 – 14:47")
        assertThat(card.formattedDuration).isEqualTo("01:32:00")
    }

    @Test
    fun `cards run in the order the day happened and are numbered after sorting`() {
        val detail =
            day(
                task(
                    intervals =
                        listOf(
                            interval("2026-09-08T15:30:00Z", minutes = 34, id = "late"),
                            interval("2026-09-08T09:30:00Z", minutes = 42, id = "early"),
                            interval("2026-09-08T10:20:00Z", minutes = 38, id = "middle"),
                        ),
                ),
            )

        assertThat(detail.intervals.map { it.intervalId }).isEqualTo(listOf("early", "middle", "late"))
        assertThat(detail.intervals.map { it.indexLabel }).isEqualTo(listOf("01", "02", "03"))
    }

    @Test
    fun `an interval running past midnight appears on both days, each reading forwards`() {
        val crossing = task(intervals = listOf(interval("2026-09-08T23:40:00Z", minutes = 40)))

        val first = day(crossing)
        // 24:00, not 00:00: the same instant named from this day's side, so the range reads
        // forwards instead of looking like a session that ran backwards.
        assertThat(first.intervals.single().timeRangeLabel).isEqualTo("23:40 – 24:00")
        assertThat(first.totalDuration).isEqualTo("00:20:00")

        val second = day(crossing, on = LocalDate(2026, 9, 9))
        assertThat(second.intervals.single().timeRangeLabel).isEqualTo("00:00 – 00:20")
        assertThat(second.totalDuration).isEqualTo("00:20:00")

        // One task's worth of work, counted once on each day it touched.
        assertThat(first.taskCount).isEqualTo(1)
        assertThat(second.taskCount).isEqualTo(1)
    }

    @Test
    fun `a zero length interval is still listed`() {
        val detail = day(task(intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 0))))

        assertThat(detail.intervalCount).isEqualTo(1)
        assertThat(detail.intervals.single().formattedDuration).isEqualTo("00:00:00")
    }

    @Test
    fun `an open interval is not listed`() {
        val detail = day(task(intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 30, open = true))))

        assertThat(detail.isEmpty).isTrue()
    }

    // --- the counts and the total ---------------------------------------------------------------

    @Test
    fun `the total sums the day's intervals`() {
        val detail =
            day(
                task(
                    intervals =
                        listOf(
                            interval("2026-09-08T09:30:00Z", minutes = 42, id = "a"),
                            interval("2026-09-08T13:15:00Z", minutes = 92, id = "b"),
                            interval("2026-09-08T15:30:00Z", minutes = 34, id = "c"),
                        ),
                ),
            )

        assertThat(detail.totalDuration).isEqualTo("02:48:00")
    }

    @Test
    fun `the total and the cards carry seconds, and the seconds carry into minutes`() {
        val detail =
            day(
                task(
                    intervals =
                        listOf(
                            interval("2026-09-08T09:30:00Z", minutes = 1, seconds = 30, id = "a"),
                            interval("2026-09-08T13:15:00Z", minutes = 1, seconds = 28, id = "b"),
                        ),
                ),
            )

        // 90s + 88s is 178s: the seconds roll over into a second minute rather than being dropped.
        assertThat(detail.totalDuration).isEqualTo("00:02:58")
        assertThat(detail.intervals.first().formattedDuration).isEqualTo("00:01:30")
    }

    @Test
    fun `taskCount counts distinct tasks, not intervals`() {
        val detail =
            day(
                task(
                    id = "task-a",
                    intervals =
                        listOf(
                            interval("2026-09-08T09:00:00Z", minutes = 10, id = "a1"),
                            interval("2026-09-08T11:00:00Z", minutes = 10, id = "a2"),
                        ),
                ),
                task(id = "task-b", intervals = listOf(interval("2026-09-08T13:00:00Z", minutes = 10, id = "b1"))),
            )

        assertThat(detail.intervalCount).isEqualTo(3)
        assertThat(detail.taskCount).isEqualTo(2)
    }

    @Test
    fun `two subtasks of one task count as one task`() {
        val detail =
            day(
                task(
                    id = "task-a",
                    subTasks =
                        listOf(
                            subTask(
                                id = "sub-a",
                                title = "A",
                                intervals = listOf(subInterval("2026-09-08T09:00:00Z", minutes = 10, id = "a")),
                            ),
                            subTask(
                                id = "sub-b",
                                title = "B",
                                intervals = listOf(subInterval("2026-09-08T11:00:00Z", minutes = 10, id = "b")),
                            ),
                        ),
                ),
            )

        assertThat(detail.intervalCount).isEqualTo(2)
        assertThat(detail.taskCount).isEqualTo(1)
    }

    // --- agreement with the calendar ------------------------------------------------------------

    @Test
    fun `a task with subtasks is not double counted, so the day agrees with its calendar cell`() {
        val tasks =
            arrayOf(
                task(
                    intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 42)),
                    subTasks = listOf(subTask(intervals = listOf(subInterval("2026-09-08T09:00:00Z", minutes = 42)))),
                ),
            )
        val detail = day(*tasks)

        assertThat(detail.intervalCount).isEqualTo(1)
        assertThat(detail.totalDuration).isEqualTo("00:42:00")

        // The number the calendar tints that cell with must be the number the list totals.
        val cell =
            project(tasks = tasks.toList())
                .toCalendarMonthsUi(today = sep8, timeZone = TimeZone.UTC)
                .flatMap { it.monthDays }
                .single { it.date == sep8 }
        assertThat("00:42:00").isEqualTo(detail.totalDuration)
        assertThat(cell.trackedMillis).isEqualTo(42 * 60_000L)
    }

    @Test
    fun `the zone decides which day an interval is listed under`() {
        val tasks = arrayOf(task(intervals = listOf(interval("2026-09-05T02:00:00Z", minutes = 30))))
        val ny = TimeZone.of("America/New_York")

        assertThat(day(*tasks, on = LocalDate(2026, 9, 5)).intervalCount == 1).isTrue()
        assertThat(day(*tasks, on = LocalDate(2026, 9, 5), zone = ny).isEmpty).isTrue()
        assertThat(
            day(*tasks, on = LocalDate(2026, 9, 4), zone = ny).intervals.single().timeRangeLabel,
        ).isEqualTo("22:00 – 22:30")
    }
}
