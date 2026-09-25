package com.jvcs.tracky.features.project.domain.export

import com.jvcs.tracky.features.project.domain.interval.splitAcrossLocalDays
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.models.status
import com.jvcs.tracky.features.project.domain.task.sortedBySubTaskOrder
import com.jvcs.tracky.features.project.domain.task.sortedByTaskOrder
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Computes everything a project export shows. Pure: [timeZone] and [now] are passed in.
 *
 * Day figures count **task intervals only** — subtask intervals nest inside them, so adding both
 * bills the same minutes twice. Open intervals are listed (ending at [now]) but bank no day time,
 * and an interval crossing midnight is shared out between the days it touches.
 */
fun Project.toProjectReport(timeZone: TimeZone, now: Instant): ProjectReport {
    val tasks = projectTasks.orEmpty().sortedByTaskOrder()
    val total = tasks.sumOf { it.durationMillis ?: 0L }.milliseconds
    val dayTotals = tasks.flatMap { it.intervals }.dayTotals(timeZone)
    return ProjectReport(
        title = title,
        description = description,
        colorArgb = colorArgb,
        status = status,
        exportedAt = now.toLocalDateTime(timeZone).date,
        summary =
            ReportSummary(
                totalTracked = total,
                taskCount = tasks.size,
                finishedTaskCount = tasks.count { it.isFinished },
                openTaskCount = tasks.count { !it.isFinished },
                intervalCount = tasks.sumOf { it.intervals.size },
                activeDays = dayTotals.size,
                firstActiveDate = dayTotals.keys.minOrNull(),
                lastActiveDate = dayTotals.keys.maxOrNull(),
            ),
        tasks = tasks.map { it.toReportTask(total, timeZone, now) },
        months = dayTotals.toMonths(),
    )
}

private fun ProjectTask.toReportTask(
    total: Duration,
    timeZone: TimeZone,
    now: Instant,
): ReportTask {
    val duration = (durationMillis ?: 0L).milliseconds
    val subTasks = subTasks.orEmpty()
    val titleByInterval =
        subTasks
            .flatMap { subTask -> subTask.subTaskIntervals.map { it to subTask.title } }
            .sortedBy { (interval, _) -> interval.startDateTimeUtc }
            .groupBy({ (interval, _) -> interval.parentTaskIntervalId }, { (_, title) -> title })
    return ReportTask(
        title = title,
        description = description,
        isFinished = isFinished,
        duration = duration,
        shareFraction = if (total.isPositive()) (duration / total).toFloat() else 0f,
        startDate = startDateTimeUtc.toLocalDateTime(timeZone).date,
        endDate = endDateTimeUtc?.takeIf { isFinished }?.toLocalDateTime(timeZone)?.date,
        subtasks =
            subTasks.sortedBySubTaskOrder().map { subTask ->
                ReportSubtask(
                    title = subTask.title,
                    isFinished = subTask.isFinished,
                    intervalCount = subTask.subTaskIntervals.size,
                    duration = (subTask.durationMillis ?: 0L).milliseconds,
                )
            },
        intervals =
            intervals.sortedBy { it.startDateTimeUtc }.map { interval ->
                val start = interval.startDateTimeUtc.toLocalDateTime(timeZone)
                val end = interval.endDateTimeUtc
                val running = now - interval.startDateTimeUtc
                ReportInterval(
                    date = start.date,
                    start = start.time,
                    end = (end ?: now).toLocalDateTime(timeZone).time,
                    isRunning = end == null,
                    subtaskTitles = titleByInterval[interval.intervalId].orEmpty().distinct(),
                    duration = if (end == null) running else interval.durationMillis.milliseconds,
                )
            },
    )
}

/** Banked time per local day, keeping only days that banked some. */
private fun List<TaskInterval>.dayTotals(timeZone: TimeZone): Map<LocalDate, Duration> =
    flatMap { interval ->
        val end = interval.endDateTimeUtc ?: return@flatMap emptyList()
        splitAcrossLocalDays(interval.startDateTimeUtc, end, interval.durationMillis, timeZone)
    }.groupingBy { it.date }
        .fold(0L) { millis, slice -> millis + slice.durationMillis }
        .filterValues { it > 0L }
        .mapValues { (_, millis) -> millis.milliseconds }

private fun Map<LocalDate, Duration>.toMonths(): List<ReportMonth> {
    val first = keys.minOrNull() ?: return emptyList()
    val last = keys.max().yearMonth
    return generateSequence(first.yearMonth.firstDay) { it.plus(1, DateTimeUnit.MONTH) }
        .takeWhile { it.yearMonth <= last }
        .map { firstDay ->
            val days = filterKeys { it.yearMonth == firstDay.yearMonth }
            ReportMonth(
                year = firstDay.year,
                month = firstDay.month,
                total = days.values.fold(Duration.ZERO, Duration::plus),
                dayTotals = days,
            )
        }.toList()
}
