package com.jvcs.tracky.features.project.presentation.mappers

import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.presentation.fakes.interval
import com.jvcs.tracky.features.project.presentation.fakes.project
import com.jvcs.tracky.features.project.presentation.fakes.subInterval
import com.jvcs.tracky.features.project.presentation.fakes.subTask
import com.jvcs.tracky.features.project.presentation.fakes.task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

        assertTrue(detail.isEmpty)
        assertEquals("Tue, Sep 08", detail.dateLabel)
        assertEquals("00:00:00", detail.totalDuration)
        assertEquals(0, detail.taskCount)
        assertEquals(0, detail.intervalCount)
    }

    @Test
    fun `the headline carries the year, which the day heading does not`() {
        val detail = day(on = LocalDate(2026, 10, 15))

        assertEquals("Thu, Oct 15", detail.dateLabel)
        // Unpadded day, and a year: the calendar can be paged years away from today.
        assertEquals("Oct 15, 2026", detail.headlineLabel)
        assertEquals("Sep 8, 2026", day().headlineLabel)
    }

    @Test
    fun `intervals from other days are left out`() {
        val detail = day(task(intervals = listOf(interval("2026-09-07T09:00:00Z", minutes = 30))))

        assertTrue(detail.isEmpty)
    }

    // --- the cards ----------------------------------------------------------------------------

    @Test
    fun `a card carries the task title, the range and the duration`() {
        val detail =
            day(
                task(title = "Design review", intervals = listOf(interval("2026-09-08T09:30:00Z", minutes = 42))),
            )

        val card = detail.intervals.single()
        assertEquals("01", card.indexLabel)
        assertEquals("Design review", card.taskTitle)
        assertNull(card.subTaskTitle)
        assertEquals("09:30 – 10:12", card.timeRangeLabel)
        assertEquals("00:42:00", card.formattedDuration)
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
        assertEquals("Auth endpoints", card.taskTitle)
        assertEquals("Token refresh", card.subTaskTitle)
        assertEquals("13:15 – 14:47", card.timeRangeLabel)
        assertEquals("01:32:00", card.formattedDuration)
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

        assertEquals(listOf("early", "middle", "late"), detail.intervals.map { it.intervalId })
        assertEquals(listOf("01", "02", "03"), detail.intervals.map { it.indexLabel })
    }

    @Test
    fun `an interval running past midnight appears on both days, each reading forwards`() {
        val crossing = task(intervals = listOf(interval("2026-09-08T23:40:00Z", minutes = 40)))

        val first = day(crossing)
        // 24:00, not 00:00: the same instant named from this day's side, so the range reads
        // forwards instead of looking like a session that ran backwards.
        assertEquals("23:40 – 24:00", first.intervals.single().timeRangeLabel)
        assertEquals("00:20:00", first.totalDuration)

        val second = day(crossing, on = LocalDate(2026, 9, 9))
        assertEquals("00:00 – 00:20", second.intervals.single().timeRangeLabel)
        assertEquals("00:20:00", second.totalDuration)

        // One task's worth of work, counted once on each day it touched.
        assertEquals(1, first.taskCount)
        assertEquals(1, second.taskCount)
    }

    @Test
    fun `a zero length interval is still listed`() {
        val detail = day(task(intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 0))))

        assertEquals(1, detail.intervalCount)
        assertEquals("00:00:00", detail.intervals.single().formattedDuration)
    }

    @Test
    fun `an open interval is not listed`() {
        val detail = day(task(intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 30, open = true))))

        assertTrue(detail.isEmpty)
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

        assertEquals("02:48:00", detail.totalDuration)
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
        assertEquals("00:02:58", detail.totalDuration)
        assertEquals("00:01:30", detail.intervals.first().formattedDuration)
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

        assertEquals(3, detail.intervalCount)
        assertEquals(2, detail.taskCount)
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

        assertEquals(2, detail.intervalCount)
        assertEquals(1, detail.taskCount)
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

        assertEquals(1, detail.intervalCount)
        assertEquals("00:42:00", detail.totalDuration)

        // The number the calendar tints that cell with must be the number the list totals.
        val cell =
            project(tasks = tasks.toList())
                .toCalendarMonthsUi(today = sep8, timeZone = TimeZone.UTC)
                .flatMap { it.monthDays }
                .single { it.date == sep8 }
        assertEquals(detail.totalDuration, "00:42:00")
        assertEquals(42 * 60_000L, cell.trackedMillis)
    }

    @Test
    fun `the zone decides which day an interval is listed under`() {
        val tasks = arrayOf(task(intervals = listOf(interval("2026-09-05T02:00:00Z", minutes = 30))))
        val ny = TimeZone.of("America/New_York")

        assertTrue(day(*tasks, on = LocalDate(2026, 9, 5)).intervalCount == 1)
        assertTrue(day(*tasks, on = LocalDate(2026, 9, 5), zone = ny).isEmpty)
        assertEquals(
            "22:00 – 22:30",
            day(*tasks, on = LocalDate(2026, 9, 4), zone = ny).intervals.single().timeRangeLabel,
        )
    }
}
