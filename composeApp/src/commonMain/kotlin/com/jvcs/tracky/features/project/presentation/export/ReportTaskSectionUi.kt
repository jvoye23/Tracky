package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.runtime.Immutable
import com.jvcs.tracky.features.project.domain.export.ReportInterval
import com.jvcs.tracky.features.project.domain.export.ReportSubtask
import com.jvcs.tracky.features.project.domain.export.ReportTask

/** Shown in an interval row that timed no subtask. */
private const val NO_SUBTASK = "—"

/** One task's detail pages: heading, subtasks and every interval. */
@Immutable
data class ReportTaskSectionUi(
    val title: String,
    val description: String?,
    val isFinished: Boolean,
    /** "14 Aug 2026", or "14 Aug 2026 – 27 Aug 2026" once finished. */
    val period: String,
    val intervalCount: Int,
    /** "04:21:39". */
    val duration: String,
    val subtasks: List<ReportSubtaskRowUi>,
    val intervals: List<ReportIntervalRowUi>,
)

@Immutable
data class ReportSubtaskRowUi(
    val title: String,
    val isFinished: Boolean,
    val intervalCount: Int,
    /** "05:30:48". */
    val duration: String,
)

@Immutable
data class ReportIntervalRowUi(
    /** "Wed 02 Sept". */
    val date: String,
    /** "06:58 – 07:05". */
    val time: String,
    /** "Subtask Number One, subtask update", or "—" when no subtask was timed. */
    val subtasks: String,
    /** "00:00:54". */
    val duration: String,
)

internal fun ReportTask.toReportTaskSectionUi(): ReportTaskSectionUi =
    ReportTaskSectionUi(
        title = title,
        // The app fills an untouched description with the title; repeating it says nothing.
        description = description?.trim()?.takeIf { it.isNotEmpty() && it != title.trim() },
        isFinished = isFinished,
        period = endDate?.let { formatReportDateRange(startDate, it) } ?: formatReportDate(startDate),
        intervalCount = intervals.size,
        duration = formatReportDuration(duration),
        subtasks = subtasks.map { it.toReportSubtaskRowUi() },
        intervals = intervals.map { it.toReportIntervalRowUi() },
    )

private fun ReportSubtask.toReportSubtaskRowUi() =
    ReportSubtaskRowUi(
        title = title,
        isFinished = isFinished,
        intervalCount = intervalCount,
        duration = formatReportDuration(duration),
    )

private fun ReportInterval.toReportIntervalRowUi() =
    ReportIntervalRowUi(
        date = formatIntervalDate(date),
        time = formatClockRange(start, end),
        subtasks = subtaskTitles.joinToString(", ").ifEmpty { NO_SUBTASK },
        duration = formatReportDuration(duration),
    )
