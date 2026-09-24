package com.jvcs.tracky.features.project.presentation.mappers

import com.jvcs.tracky.features.project.presentation.fakes.interval
import com.jvcs.tracky.features.project.presentation.fakes.project
import com.jvcs.tracky.features.project.presentation.fakes.subInterval
import com.jvcs.tracky.features.project.presentation.fakes.subTask
import com.jvcs.tracky.features.project.presentation.fakes.task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The three rules that live in [countedDayIntervals] and nowhere else: subtask-nesting dedupe,
 * open intervals dropped, and an interval split at every local midnight it crosses.
 *
 * Pure — data in, data out. The zone is always explicit, because it is the only thing that
 * decides which local day an interval lands on.
 */
class CountedDayIntervalsTest {

    // --- empty cases -------------------------------------------------------------------------

    @Test
    fun `a project with no tasks counts nothing`() {
        assertTrue(project().countedDayIntervals(TimeZone.UTC).isEmpty())
    }

    @Test
    fun `a task with no intervals counts nothing`() {
        assertTrue(project(tasks = listOf(task())).countedDayIntervals(TimeZone.UTC).isEmpty())
    }

    @Test
    fun `tasks that were never loaded count nothing`() {
        val counted =
            project(tasks = emptyList())
                .copy(projectTasks = null)
                .countedDayIntervals(TimeZone.UTC)

        assertTrue(counted.isEmpty())
    }

    // --- rule 2: open intervals are dropped ---------------------------------------------------

    @Test
    fun `an open task interval is dropped`() {
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 30, open = true)))),
            ).countedDayIntervals(TimeZone.UTC)

        assertTrue(counted.isEmpty())
    }

    @Test
    fun `an open subtask interval is dropped but its closed sibling survives`() {
        val counted =
            project(
                tasks =
                    listOf(
                        task(
                            subTasks =
                                listOf(
                                    subTask(
                                        intervals =
                                            listOf(
                                                subInterval("2026-09-08T09:00:00Z", minutes = 20),
                                                subInterval("2026-09-08T11:00:00Z", minutes = 30, open = true),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            ).countedDayIntervals(TimeZone.UTC)

        assertEquals(1, counted.size)
        assertEquals(20 * 60_000L, counted.single().durationMillis)
    }

    // --- rule 1: subtask nesting dedupe -------------------------------------------------------

    @Test
    fun `a task without subtasks contributes its own intervals`() {
        val counted =
            project(
                tasks =
                    listOf(
                        task(
                            title = "Design review",
                            intervals = listOf(interval("2026-09-08T09:30:00Z", minutes = 42)),
                        ),
                    ),
            ).countedDayIntervals(TimeZone.UTC)

        assertEquals(1, counted.size)
        assertEquals("Design review", counted.single().taskTitle)
        assertNull(counted.single().subTaskTitle)
    }

    @Test
    fun `a task with subtasks contributes only its subtasks' intervals`() {
        // The task interval and the subtask interval describe the same stretch of wall clock:
        // counting both would bill 42 minutes twice.
        val counted =
            project(
                tasks =
                    listOf(
                        task(
                            title = "Auth endpoints",
                            intervals = listOf(interval("2026-09-08T09:30:00Z", minutes = 42)),
                            subTasks =
                                listOf(
                                    subTask(
                                        title = "Token refresh",
                                        intervals = listOf(subInterval("2026-09-08T09:30:00Z", minutes = 42)),
                                    ),
                                ),
                        ),
                    ),
            ).countedDayIntervals(TimeZone.UTC)

        assertEquals(1, counted.size)
        assertEquals(42 * 60_000L, counted.sumOf { it.durationMillis })
        assertEquals("Token refresh", counted.single().subTaskTitle)
    }

    @Test
    fun `a subtask interval is titled by its owning task, not the subtask`() {
        val counted =
            project(
                tasks =
                    listOf(
                        task(
                            id = "task-7",
                            title = "Auth endpoints",
                            subTasks =
                                listOf(
                                    subTask(
                                        title = "Token refresh",
                                        intervals = listOf(subInterval("2026-09-08T09:30:00Z", minutes = 42)),
                                    ),
                                ),
                        ),
                    ),
            ).countedDayIntervals(TimeZone.UTC)

        assertEquals("Auth endpoints", counted.single().taskTitle)
        assertEquals("Token refresh", counted.single().subTaskTitle)
        assertEquals("task-7", counted.single().taskId)
    }

    @Test
    fun `every subtask under a task contributes`() {
        val counted =
            project(
                tasks =
                    listOf(
                        task(
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
                                        intervals = listOf(subInterval("2026-09-08T10:00:00Z", minutes = 25, id = "b")),
                                    ),
                                ),
                        ),
                    ),
            ).countedDayIntervals(TimeZone.UTC)

        assertEquals(listOf("A", "B"), counted.map { it.subTaskTitle })
        assertEquals(35 * 60_000L, counted.sumOf { it.durationMillis })
    }

    // --- rule 3: an interval belongs to the day it started on ---------------------------------

    @Test
    fun `an interval is dated by its local start day`() {
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T09:30:00Z", minutes = 42)))),
            ).countedDayIntervals(TimeZone.UTC)

        assertEquals(LocalDate(2026, 9, 8), counted.single().date)
        assertEquals(LocalTime(9, 30), counted.single().start)
        assertEquals(LocalTime(10, 12), counted.single().end)
    }

    @Test
    fun `an interval running past midnight is split, each day taking its own share`() {
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T23:40:00Z", minutes = 40)))),
            ).countedDayIntervals(TimeZone.UTC)

        // Two entries, not one: billing all 40 minutes to the 8th is what let a single day total
        // more than 24 hours.
        assertEquals(2, counted.size)

        assertEquals(LocalDate(2026, 9, 8), counted[0].date)
        assertEquals(LocalTime(23, 40), counted[0].start)
        assertEquals(20 * 60 * 1000L, counted[0].durationMillis)
        assertTrue(counted[0].endsAtMidnight, "cut at the boundary, so the label reads 24:00")
        assertEquals(0, counted[0].sliceIndex)
        assertEquals(2, counted[0].sliceCount)

        assertEquals(LocalDate(2026, 9, 9), counted[1].date)
        assertEquals(LocalTime(0, 0), counted[1].start)
        assertEquals(LocalTime(0, 20), counted[1].end)
        assertEquals(20 * 60 * 1000L, counted[1].durationMillis)
        assertFalse(counted[1].endsAtMidnight)

        // Same row on both days: the id still names the interval a delete would act on.
        assertEquals(counted[0].intervalId, counted[1].intervalId)
    }

    @Test
    fun `a split interval still sums to what was banked`() {
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T23:40:00Z", minutes = 40)))),
            ).countedDayIntervals(TimeZone.UTC)

        assertEquals(40 * 60 * 1000L, counted.sumOf { it.durationMillis })
    }

    @Test
    fun `a same-day interval is still a single entry`() {
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T09:30:00Z", minutes = 42)))),
            ).countedDayIntervals(TimeZone.UTC)

        // The common case must not move: one row in, one row out, sliceCount 1.
        assertEquals(1, counted.size)
        assertEquals(1, counted.single().sliceCount)
        assertFalse(counted.single().endsAtMidnight)
    }

    @Test
    fun `the zone decides the local day`() {
        val tasks = listOf(task(intervals = listOf(interval("2026-09-05T02:00:00Z", minutes = 30))))

        assertEquals(LocalDate(2026, 9, 5), project(tasks = tasks).countedDayIntervals(TimeZone.UTC).single().date)
        assertEquals(
            LocalDate(2026, 9, 4),
            project(tasks = tasks).countedDayIntervals(TimeZone.of("America/New_York")).single().date,
        )
    }

    @Test
    fun `one interval never yields two entries for the same day`() {
        // The daily overview keys its LazyColumn on intervalId after filtering to a single date,
        // so this is what makes that key unique. A split puts each slice on a different day.
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T23:40:00Z", minutes = 40)))),
            ).countedDayIntervals(TimeZone.UTC)

        val perDayIds = counted.groupBy { it.date }.mapValues { (_, slices) -> slices.map { it.intervalId } }
        perDayIds.forEach { (date, ids) ->
            assertEquals(ids.size, ids.distinct().size, "duplicate interval id on $date")
        }
    }

    // --- zero length is the caller's decision -------------------------------------------------

    @Test
    fun `a zero length interval is counted here and left for the caller to filter`() {
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 0)))),
            ).countedDayIntervals(TimeZone.UTC)

        assertEquals(1, counted.size)
        assertEquals(0L, counted.single().durationMillis)
    }
}
