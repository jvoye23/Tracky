package com.jvcs.tracky.features.project.presentation.mappers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.features.project.presentation.fakes.interval
import com.jvcs.tracky.features.project.presentation.fakes.project
import com.jvcs.tracky.features.project.presentation.fakes.subInterval
import com.jvcs.tracky.features.project.presentation.fakes.subTask
import com.jvcs.tracky.features.project.presentation.fakes.task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test

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
        assertThat(project().countedDayIntervals(TimeZone.UTC).isEmpty()).isTrue()
    }

    @Test
    fun `a task with no intervals counts nothing`() {
        assertThat(project(tasks = listOf(task())).countedDayIntervals(TimeZone.UTC).isEmpty()).isTrue()
    }

    @Test
    fun `tasks that were never loaded count nothing`() {
        val counted =
            project(tasks = emptyList())
                .copy(projectTasks = null)
                .countedDayIntervals(TimeZone.UTC)

        assertThat(counted.isEmpty()).isTrue()
    }

    // --- rule 2: open intervals are dropped ---------------------------------------------------

    @Test
    fun `an open task interval is dropped`() {
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 30, open = true)))),
            ).countedDayIntervals(TimeZone.UTC)

        assertThat(counted.isEmpty()).isTrue()
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

        assertThat(counted.size).isEqualTo(1)
        assertThat(counted.single().durationMillis).isEqualTo(20 * 60_000L)
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

        assertThat(counted.size).isEqualTo(1)
        assertThat(counted.single().taskTitle).isEqualTo("Design review")
        assertThat(counted.single().subTaskTitle).isNull()
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

        assertThat(counted.size).isEqualTo(1)
        assertThat(counted.sumOf { it.durationMillis }).isEqualTo(42 * 60_000L)
        assertThat(counted.single().subTaskTitle).isEqualTo("Token refresh")
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

        assertThat(counted.single().taskTitle).isEqualTo("Auth endpoints")
        assertThat(counted.single().subTaskTitle).isEqualTo("Token refresh")
        assertThat(counted.single().taskId).isEqualTo("task-7")
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

        assertThat(counted.map { it.subTaskTitle }).isEqualTo(listOf("A", "B"))
        assertThat(counted.sumOf { it.durationMillis }).isEqualTo(35 * 60_000L)
    }

    // --- rule 3: an interval belongs to the day it started on ---------------------------------

    @Test
    fun `an interval is dated by its local start day`() {
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T09:30:00Z", minutes = 42)))),
            ).countedDayIntervals(TimeZone.UTC)

        assertThat(counted.single().date).isEqualTo(LocalDate(2026, 9, 8))
        assertThat(counted.single().start).isEqualTo(LocalTime(9, 30))
        assertThat(counted.single().end).isEqualTo(LocalTime(10, 12))
    }

    @Test
    fun `an interval running past midnight is split, each day taking its own share`() {
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T23:40:00Z", minutes = 40)))),
            ).countedDayIntervals(TimeZone.UTC)

        // Two entries, not one: billing all 40 minutes to the 8th is what let a single day total
        // more than 24 hours.
        assertThat(counted.size).isEqualTo(2)

        assertThat(counted[0].date).isEqualTo(LocalDate(2026, 9, 8))
        assertThat(counted[0].start).isEqualTo(LocalTime(23, 40))
        assertThat(counted[0].durationMillis).isEqualTo(20 * 60 * 1000L)
        assertThat(counted[0].endsAtMidnight, name = "cut at the boundary, so the label reads 24:00").isTrue()
        assertThat(counted[0].sliceIndex).isEqualTo(0)
        assertThat(counted[0].sliceCount).isEqualTo(2)

        assertThat(counted[1].date).isEqualTo(LocalDate(2026, 9, 9))
        assertThat(counted[1].start).isEqualTo(LocalTime(0, 0))
        assertThat(counted[1].end).isEqualTo(LocalTime(0, 20))
        assertThat(counted[1].durationMillis).isEqualTo(20 * 60 * 1000L)
        assertThat(counted[1].endsAtMidnight).isFalse()

        // Same row on both days: the id still names the interval a delete would act on.
        assertThat(counted[1].intervalId).isEqualTo(counted[0].intervalId)
    }

    @Test
    fun `a split interval still sums to what was banked`() {
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T23:40:00Z", minutes = 40)))),
            ).countedDayIntervals(TimeZone.UTC)

        assertThat(counted.sumOf { it.durationMillis }).isEqualTo(40 * 60 * 1000L)
    }

    @Test
    fun `a same-day interval is still a single entry`() {
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T09:30:00Z", minutes = 42)))),
            ).countedDayIntervals(TimeZone.UTC)

        // The common case must not move: one row in, one row out, sliceCount 1.
        assertThat(counted.size).isEqualTo(1)
        assertThat(counted.single().sliceCount).isEqualTo(1)
        assertThat(counted.single().endsAtMidnight).isFalse()
    }

    @Test
    fun `the zone decides the local day`() {
        val tasks = listOf(task(intervals = listOf(interval("2026-09-05T02:00:00Z", minutes = 30))))

        assertThat(
            project(tasks = tasks).countedDayIntervals(TimeZone.UTC).single().date,
        ).isEqualTo(LocalDate(2026, 9, 5))
        assertThat(
            project(tasks = tasks).countedDayIntervals(TimeZone.of("America/New_York")).single().date,
        ).isEqualTo(LocalDate(2026, 9, 4))
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
            assertThat(ids.distinct().size, name = "duplicate interval id on $date").isEqualTo(ids.size)
        }
    }

    // --- zero length is the caller's decision -------------------------------------------------

    @Test
    fun `a zero length interval is counted here and left for the caller to filter`() {
        val counted =
            project(
                tasks = listOf(task(intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 0)))),
            ).countedDayIntervals(TimeZone.UTC)

        assertThat(counted.size).isEqualTo(1)
        assertThat(counted.single().durationMillis).isEqualTo(0L)
    }
}
