package com.jvcs.tracky.features.project.domain.export

import com.jvcs.tracky.features.project.domain.models.ProjectStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlin.time.Duration

/** Everything a project export shows, computed but not formatted — renderers only format and draw. */
data class ProjectReport(
    val title: String,
    val description: String?,
    val colorArgb: Int?,
    val status: ProjectStatus,
    val exportedAt: LocalDate,
    val summary: ReportSummary,
    /** In task order; feeds both the task table and the per-task sections. */
    val tasks: List<ReportTask>,
    /** One per calendar month from the first to the last active one, empty months included. */
    val months: List<ReportMonth>,
)

/** Doubles as the task table's total row. [totalTracked] sums banked task durations; day figures sum intervals. */
data class ReportSummary(
    val totalTracked: Duration,
    val taskCount: Int,
    val finishedTaskCount: Int,
    val openTaskCount: Int,
    val intervalCount: Int,
    val activeDays: Int,
    val firstActiveDate: LocalDate?,
    val lastActiveDate: LocalDate?,
)

data class ReportTask(
    val title: String,
    val description: String?,
    val isFinished: Boolean,
    val duration: Duration,
    /** This task's part of [ReportSummary.totalTracked], 0..1; 0 when nothing was tracked. */
    val shareFraction: Float,
    val startDate: LocalDate,
    /** Set only for a finished task. */
    val endDate: LocalDate?,
    val subtasks: List<ReportSubtask>,
    val intervals: List<ReportInterval>,
)

data class ReportSubtask(
    val title: String,
    val isFinished: Boolean,
    val intervalCount: Int,
    val duration: Duration,
)

/** One task interval. Its subtask intervals are named, never listed, since they nest inside it. */
data class ReportInterval(
    val date: LocalDate,
    val start: LocalTime,
    /** The export moment's wall-clock time while [isRunning]. */
    val end: LocalTime,
    val isRunning: Boolean,
    /** Subtasks timed inside this interval, distinct, in the order they were first started. */
    val subtaskTitles: List<String>,
    val duration: Duration,
)

data class ReportMonth(
    val year: Int,
    val month: Month,
    val total: Duration,
    /** Only days that banked time. */
    val dayTotals: Map<LocalDate, Duration>,
) {

    /** The scale a heat map's intensity is drawn against. */
    val maxDayTotal: Duration get() = dayTotals.values.maxOrNull() ?: Duration.ZERO
}
