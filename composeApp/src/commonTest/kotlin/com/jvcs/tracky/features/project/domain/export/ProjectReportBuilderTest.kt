package com.jvcs.tracky.features.project.domain.export

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectStatus
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ProjectReportBuilderTest {

    private val now = Instant.parse("2026-09-22T10:00:00Z")
    private val created = Instant.parse("2026-01-05T08:00:00Z")

    private fun report(vararg tasks: ProjectTask, zone: TimeZone = TimeZone.UTC) =
        Project("p", "Report", "About", 42, null, created, false, endDateTimeUtc = null, projectTasks = tasks.toList())
            .toProjectReport(zone, now)

    private fun task(title: String = "t", minutes: Long? = 0) =
        ProjectTask("t", title, null, minutes?.times(60_000), created, parentProjectId = "p", isTimerRunning = false)

    /** Closed and [minutes] long; its id is its [start]. */
    private fun interval(start: String, minutes: Long) =
        Instant.parse(start).let {
            TaskInterval(start, "t", "p", it, it + minutes.minutes, minutes.minutes.inWholeMilliseconds)
        }

    private fun running(start: String) = interval(start, 0).copy(endDateTimeUtc = null)

    /** One minute-long interval per start, each nested in the task interval [inside]. */
    private fun subTask(
        title: String,
        inside: String,
        vararg starts: String,
    ) = ProjectSubTask(title, "t", "p", title, null, starts.size * 60_000L, false, created).copy(
        subTaskIntervals =
            starts.map {
                val at = Instant.parse(it)
                SubTaskInterval("$title@$it", inside, title, "p", at, at + 1.minutes, 60_000)
            },
    )

    @Test
    fun aProjectWithNothingTrackedReportsZeros() {
        val report = report()

        assertThat(listOf(report.title, report.description, report.colorArgb)).containsExactly("Report", "About", 42)
        assertThat(report.status).isEqualTo(ProjectStatus.ACTIVE)
        assertThat(report.exportedAt).isEqualTo(LocalDate(2026, 9, 22))
        assertThat(report.summary).isEqualTo(ReportSummary(Duration.ZERO, 0, 0, 0, 0, 0, null, null))
        assertThat(report.tasks).isEmpty()
        assertThat(report.months).isEmpty()
        assertThat(report(task("a"), task("b", null)).tasks.map { it.shareFraction }).containsExactly(0f, 0f)
    }

    @Test
    fun summaryCountsTasksIntervalsAndBankedDurations() {
        val report =
            report(
                task("a", 30).copy(intervals = listOf(interval("2026-01-05T08:00:00Z", 30))),
                task("b", null).copy(isFinished = true),
                task("c", 90).copy(intervals = listOf(running("2026-09-22T09:00:00Z"))),
            )

        assertThat(report.summary.totalTracked).isEqualTo(120.minutes)
        with(report.summary) {
            assertThat(listOf(taskCount, finishedTaskCount, openTaskCount, intervalCount)).containsExactly(3, 1, 2, 2)
        }
        assertThat(report.tasks.map { it.shareFraction }).containsExactly(0.25f, 0f, 0.75f)
    }

    @Test
    fun tasksFollowTheManualOrderAndOnlyFinishedOnesCarryAnEndDate() {
        val finishedAt = Instant.parse("2026-03-02T08:00:00Z")
        val done = task("done").copy(sortIndex = 0, isFinished = true, endDateTimeUtc = finishedAt)
        val report = report(task("new"), task("open").copy(sortIndex = 1, endDateTimeUtc = now), done)

        assertThat(report.tasks.map { it.title }).containsExactly("done", "open", "new")
        assertThat(report.tasks.map { it.endDate }).containsExactly(LocalDate(2026, 3, 2), null, null)
        assertThat(report.tasks.first().startDate).isEqualTo(LocalDate(2026, 1, 5))
    }

    @Test
    fun monthsSpanFirstToLastActiveMonthIncludingEmptyOnes() {
        val intervals =
            listOf(
                "2026-03-10T08:00:00Z" to 45L,
                "2026-01-05T08:00:00Z" to 30L,
                "2026-01-05T12:00:00Z" to 15L,
                "2026-01-20T08:00:00Z" to 60L,
            ).map { (start, minutes) -> interval(start, minutes) }
        val report = report(task().copy(intervals = intervals))
        val (january, february, march) = report.months

        assertThat(report.summary.activeDays).isEqualTo(3)
        assertThat(report.summary.firstActiveDate to report.summary.lastActiveDate)
            .isEqualTo(LocalDate(2026, 1, 5) to LocalDate(2026, 3, 10))
        assertThat(report.months.map { it.year to it.month })
            .containsExactly(2026 to Month.JANUARY, 2026 to Month.FEBRUARY, 2026 to Month.MARCH)
        assertThat(january.total).isEqualTo(105.minutes)
        val januaryDays = mapOf(LocalDate(2026, 1, 5) to 45.minutes, LocalDate(2026, 1, 20) to 60.minutes)
        assertThat(january.dayTotals).isEqualTo(januaryDays)
        assertThat(january.maxDayTotal).isEqualTo(60.minutes)
        assertThat(listOf(february.total, february.maxDayTotal)).containsExactly(Duration.ZERO, Duration.ZERO)
        assertThat(february.dayTotals).isEmpty()
        assertThat(march.total).isEqualTo(45.minutes)
    }

    @Test
    fun dayTotalsCountClosedTaskIntervalsOnlySplitAtMidnight() {
        val tracked = interval("2026-09-21T08:00:00Z", 10)
        val nested = subTask("s", tracked.intervalId, "2026-09-21T08:01:00Z")
        val zeroLength = interval("2026-09-20T08:00:00Z", 0)
        val crossing = interval("2026-09-22T23:30:00Z", 60)
        val intervals = listOf(tracked, zeroLength, running("2026-09-22T09:00:00Z"), crossing)
        val report = report(task().copy(intervals = intervals, subTasks = listOf(nested)))

        assertThat(report.months.single().dayTotals).isEqualTo(
            mapOf(
                LocalDate(2026, 9, 21) to 10.minutes,
                LocalDate(2026, 9, 22) to 30.minutes,
                LocalDate(2026, 9, 23) to 30.minutes,
            ),
        )
        assertThat(report.summary.activeDays).isEqualTo(3)
    }

    @Test
    fun intervalsAreSortedByStartAndNameTheSubtasksTimedInsideThem() {
        val early = "2026-01-05T08:00:00Z"
        val subTasks =
            listOf(
                subTask("second", early, "2026-01-05T08:01:00Z", "2026-01-05T08:05:00Z").copy(sortIndex = 1),
                subTask("first", early, "2026-01-05T08:03:00Z").copy(sortIndex = 0),
                subTask("idle", early),
            )
        val intervals = listOf(interval("2026-01-06T08:00:00Z", 5), interval(early, 10))
        val task = report(task().copy(intervals = intervals, subTasks = subTasks)).tasks.single()

        assertThat(
            task.intervals.map { it.subtaskTitles },
        ).containsExactly(listOf("second", "first"), emptyList<String>())
        assertThat(task.intervals.map { it.duration }).containsExactly(10.minutes, 5.minutes)
        assertThat(task.subtasks)
            .containsExactly(
                ReportSubtask("first", false, 1, 1.minutes),
                ReportSubtask("second", false, 2, 2.minutes),
                ReportSubtask("idle", false, 0, Duration.ZERO),
            )
    }

    @Test
    fun aRunningIntervalEndsAtTheExportMomentInTheReportZone() {
        val running = task().copy(intervals = listOf(running("2026-09-22T09:15:00Z")))
        val report = report(running, zone = TimeZone.of("Europe/Berlin"))
        val row = report.onlyInterval()

        assertThat(row.isRunning).isTrue()
        assertThat(listOf(row.start, row.end)).containsExactly(LocalTime(11, 15), LocalTime(12, 0))
        assertThat(row.duration).isEqualTo(45.minutes)
        assertThat(report.months).isEmpty()
    }

    @Test
    fun theZoneDecidesWhichDayAnIntervalBelongsTo() {
        val lateEvening = task().copy(intervals = listOf(interval("2026-08-13T22:30:00Z", 20)))

        val utc = report(lateEvening)
        val berlin = report(lateEvening, zone = TimeZone.of("Europe/Berlin"))

        assertThat(utc.onlyInterval().date).isEqualTo(LocalDate(2026, 8, 13))
        assertThat(utc.onlyInterval().isRunning).isFalse()
        assertThat(berlin.onlyInterval().date).isEqualTo(LocalDate(2026, 8, 14))
        assertThat(berlin.onlyInterval().start).isEqualTo(LocalTime(0, 30))
        assertThat(berlin.summary.firstActiveDate).isEqualTo(LocalDate(2026, 8, 14))
        assertThat(utc.tasks.single().endDate).isNull()
    }

    private fun ProjectReport.onlyInterval() = tasks.single().intervals.single()
}
