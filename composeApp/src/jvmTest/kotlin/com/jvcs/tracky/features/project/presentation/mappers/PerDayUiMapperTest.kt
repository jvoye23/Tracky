@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project.presentation.mappers

import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The mapper is pure, so every case here is plain data in, data out — no dispatcher, no clock.
 * `today` and the zone are always passed explicitly for the same reason.
 */
class PerDayUiMapperTest {

    private val today = LocalDate(2026, 9, 8)

    // --- empty cases -------------------------------------------------------------------------

    @Test
    fun `returns null when the project has no tasks`() {
        val strip = project().toPerDayStripUi(today, TimeZone.UTC)

        assertNull(strip)
    }

    @Test
    fun `returns null when the tasks have no intervals`() {
        val strip = project(task()).toPerDayStripUi(today, TimeZone.UTC)

        assertNull(strip)
    }

    @Test
    fun `returns null when every interval is still open`() {
        val strip = project(
            task(intervals = listOf(interval("2026-09-05T09:00:00Z", minutes = 30, open = true)))
        ).toPerDayStripUi(today, TimeZone.UTC)

        assertNull(strip)
    }

    @Test
    fun `returns null when the only intervals are zero length`() {
        val strip = project(
            task(intervals = listOf(interval("2026-09-05T09:00:00Z", minutes = 0)))
        ).toPerDayStripUi(today, TimeZone.UTC)

        assertNull(strip)
    }

    // --- the strip ---------------------------------------------------------------------------

    @Test
    fun `a single tracked day on today yields one tile`() {
        val strip = project(
            task(intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 52)))
        ).toPerDayStripUi(today, TimeZone.UTC)!!

        assertEquals(1, strip.days.size)
        assertEquals("Tue", strip.days.single().weekdayLabel)
        assertEquals("08.9", strip.days.single().dateLabel)
        assertEquals("00:52:00", strip.days.single().formattedDuration)
        assertEquals(52 * 60_000L, strip.days.single().trackedMillis)
        assertEquals("Tue 08.9", strip.busiestDayLabel)
    }

    @Test
    fun `a sub-minute remainder shows up in the seconds of the label`() {
        val strip = project(
            task(intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 52, seconds = 12)))
        ).toPerDayStripUi(today, TimeZone.UTC)!!

        assertEquals("00:52:12", strip.days.single().formattedDuration)
    }

    @Test
    fun `days between two tracked days are filled in as untracked`() {
        val strip = project(
            task(
                intervals = listOf(
                    interval("2026-09-05T09:00:00Z", minutes = 60),
                    interval("2026-09-08T09:00:00Z", minutes = 12)
                )
            )
        ).toPerDayStripUi(today, TimeZone.UTC)!!

        assertEquals(listOf("05.9", "06.9", "07.9", "08.9"), strip.days.map { it.dateLabel })
        assertEquals(
            listOf("01:00:00", null, null, "00:12:00"),
            strip.days.map { it.formattedDuration }
        )
        assertEquals(listOf(0L, 0L), strip.days.subList(1, 3).map { it.trackedMillis })
    }

    @Test
    fun `the strip runs through today even when the last tracked day is earlier`() {
        val strip = project(
            task(intervals = listOf(interval("2026-09-05T09:00:00Z", minutes = 35)))
        ).toPerDayStripUi(today, TimeZone.UTC)!!

        assertEquals(4, strip.days.size)
        assertEquals("05.9", strip.days.first().dateLabel)
        assertEquals("08.9", strip.days.last().dateLabel)
        assertNull(strip.days.last().formattedDuration)
    }

    @Test
    fun `the strip starts on the first tracked day, not the project start date`() {
        val strip = project(
            task(intervals = listOf(interval("2026-09-05T09:00:00Z", minutes = 35))),
            // The project row itself starts 2026-08-01; see the project() builder.
        ).toPerDayStripUi(today, TimeZone.UTC)!!

        assertEquals("05.9", strip.days.first().dateLabel)
    }

    @Test
    fun `crossing a month boundary pads the day and leaves the month unpadded`() {
        val strip = project(
            task(
                intervals = listOf(
                    interval("2026-08-30T09:00:00Z", minutes = 10),
                    interval("2026-09-01T09:00:00Z", minutes = 10)
                )
            )
        ).toPerDayStripUi(LocalDate(2026, 9, 1), TimeZone.UTC)!!

        assertEquals(listOf("30.8", "31.8", "01.9"), strip.days.map { it.dateLabel })
        assertEquals(listOf("Sun", "Mon", "Tue"), strip.days.map { it.weekdayLabel })
    }

    // --- which intervals count -----------------------------------------------------------------

    @Test
    fun `a task with subtasks counts only its subtask intervals`() {
        // The enclosing task interval spans the whole hour its subtask worked; counting both would
        // bill that hour twice.
        val strip = project(
            task(
                intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 60)),
                subTasks = listOf(
                    subTask(listOf(subInterval("2026-09-08T09:00:00Z", minutes = 60)))
                )
            )
        ).toPerDayStripUi(today, TimeZone.UTC)!!

        assertEquals(60 * 60_000L, strip.days.single().trackedMillis)
    }

    @Test
    fun `a task with an empty subtask list still counts its own intervals`() {
        val strip = project(
            task(
                intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 20)),
                subTasks = emptyList()
            )
        ).toPerDayStripUi(today, TimeZone.UTC)!!

        assertEquals(20 * 60_000L, strip.days.single().trackedMillis)
    }

    @Test
    fun `an open interval is excluded from the total`() {
        val strip = project(
            task(
                intervals = listOf(
                    interval("2026-09-08T09:00:00Z", minutes = 20),
                    interval("2026-09-08T11:00:00Z", minutes = 99, open = true)
                )
            )
        ).toPerDayStripUi(today, TimeZone.UTC)!!

        assertEquals(20 * 60_000L, strip.days.single().trackedMillis)
    }

    @Test
    fun `intervals from several tasks on the same day are summed`() {
        val strip = project(
            task(intervals = listOf(interval("2026-09-08T09:00:00Z", minutes = 20))),
            task(intervals = listOf(interval("2026-09-08T14:00:00Z", minutes = 25)))
        ).toPerDayStripUi(today, TimeZone.UTC)!!

        assertEquals(45 * 60_000L, strip.days.single().trackedMillis)
        assertEquals("00:45:00", strip.days.single().formattedDuration)
    }

    // --- busiest day -------------------------------------------------------------------------

    @Test
    fun `the busiest day is the one with the most tracked time`() {
        val strip = project(
            task(
                intervals = listOf(
                    interval("2026-09-05T09:00:00Z", minutes = 60),
                    interval("2026-09-08T09:00:00Z", minutes = 12)
                )
            )
        ).toPerDayStripUi(today, TimeZone.UTC)!!

        assertEquals("Sat 05.9", strip.busiestDayLabel)
    }

    @Test
    fun `a tie for busiest resolves to the earlier day`() {
        val strip = project(
            task(
                intervals = listOf(
                    interval("2026-09-05T09:00:00Z", minutes = 30),
                    interval("2026-09-08T09:00:00Z", minutes = 30)
                )
            )
        ).toPerDayStripUi(today, TimeZone.UTC)!!

        assertEquals("Sat 05.9", strip.busiestDayLabel)
    }

    // --- time zones --------------------------------------------------------------------------

    @Test
    fun `an interval late in the UTC day buckets to the next local day east of UTC`() {
        // 22:30 UTC on the 4th is 08:30 on the 5th in Sydney.
        val strip = project(
            task(intervals = listOf(interval("2026-09-04T22:30:00Z", minutes = 30)))
        ).toPerDayStripUi(today, TimeZone.of("Australia/Sydney"))!!

        assertEquals("05.9", strip.days.first().dateLabel)
        assertEquals("Sat 05.9", strip.busiestDayLabel)
    }

    @Test
    fun `an interval early in the UTC day buckets to the previous local day west of UTC`() {
        // 02:00 UTC on the 5th is 22:00 on the 4th in New York.
        val strip = project(
            task(intervals = listOf(interval("2026-09-05T02:00:00Z", minutes = 30)))
        ).toPerDayStripUi(today, TimeZone.of("America/New_York"))!!

        assertEquals("04.9", strip.days.first().dateLabel)
        assertEquals("Fri 04.9", strip.busiestDayLabel)
    }

    // --- builders ----------------------------------------------------------------------------

    private fun project(vararg tasks: ProjectTask) = Project(
        projectId = "project",
        title = "Project",
        description = null,
        colorArgb = null,
        totalDurationMillis = 0L,
        startDateTimeUtc = Instant.parse("2026-08-01T00:00:00Z"),
        isFinished = false,
        endDateTimeUtc = null,
        projectTasks = tasks.toList()
    )

    private var taskCount = 0

    private fun task(
        intervals: List<TaskInterval> = emptyList(),
        subTasks: List<ProjectSubTask>? = null
    ) = ProjectTask(
        projectTaskId = "task-${taskCount++}",
        title = "Task",
        description = null,
        durationMillis = 0L,
        startDateTimeUtc = Instant.parse("2026-08-01T00:00:00Z"),
        parentProjectId = "project",
        isTimerRunning = false,
        intervals = intervals,
        subTasks = subTasks
    )

    private fun subTask(intervals: List<SubTaskInterval>) = ProjectSubTask(
        projectSubTaskId = "sub",
        parentProjectTaskId = "task-0",
        parentProjectId = "project",
        title = "Subtask",
        durationMillis = 0L,
        isTimerRunning = false,
        startDateTimeUtc = Instant.parse("2026-08-01T00:00:00Z"),
        subTaskIntervals = intervals
    )

    private var intervalCount = 0

    private fun interval(
        start: String,
        minutes: Long,
        seconds: Long = 0L,
        open: Boolean = false
    ) = TaskInterval(
        intervalId = "interval-${intervalCount++}",
        parentTaskId = "task-0",
        parentProjectId = "project",
        startDateTimeUtc = Instant.parse(start),
        endDateTimeUtc = if (open) null else Instant.parse(start),
        durationMillis = minutes * 60_000L + seconds * 1_000L
    )

    private fun subInterval(start: String, minutes: Long, open: Boolean = false) = SubTaskInterval(
        subTaskIntervalId = "sub-interval-${intervalCount++}",
        parentTaskIntervalId = "interval-0",
        parentSubTaskId = "sub",
        parentProjectId = "project",
        startDateTimeUtc = Instant.parse(start),
        endDateTimeUtc = if (open) null else Instant.parse(start),
        durationMillis = minutes * 60_000L
    )
}
