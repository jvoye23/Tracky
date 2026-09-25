package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.jvcs.tracky.designsystem.theme.reportDefaultAccent
import com.jvcs.tracky.features.project.domain.export.ProjectReport
import com.jvcs.tracky.features.project.domain.export.ReportSummary
import com.jvcs.tracky.features.project.domain.export.ReportTask
import com.jvcs.tracky.features.project.domain.models.ProjectStatus
import org.jetbrains.compose.resources.StringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.report_status_active
import tracky.composeapp.generated.resources.report_status_archived
import tracky.composeapp.generated.resources.report_status_finished
import tracky.composeapp.generated.resources.report_status_open
import tracky.composeapp.generated.resources.report_status_trashed

private const val RGB_MASK = 0xFFFFFF
private const val HEX_DIGITS = 6
private const val HEX_RADIX = 16

/** A [ProjectReport] with every figure already formatted for the page; counts stay numbers for the plurals. */
@Immutable
internal data class ProjectReportUi(
    val title: String,
    val description: String?,
    /** The project's color, or [reportDefaultAccent] when it has none. */
    val accent: Color,
    /** "#06D9E5"; `null` when the project has no color of its own. */
    val colorHex: String?,
    val statusLabel: StringResource,
    /** "22 Sept 2026". */
    val exportedDate: String,
    val summary: ReportSummaryUi,
    val taskRows: List<ReportTaskRowUi>,
    val months: List<ReportMonthUi>,
    /** In the same order as [taskRows]. */
    val taskSections: List<ReportTaskSectionUi>,
)

@Immutable
internal data class ReportSummaryUi(
    /** "08:46:20". */
    val totalTracked: String,
    val taskCount: Int,
    val finishedTaskCount: Int,
    val openTaskCount: Int,
    val intervalCount: Int,
    val activeDays: Int,
    /** "14 Aug 2026 – 22 Sept 2026"; `null` when nothing was tracked. */
    val activeDateRange: String?,
)

@Immutable
internal data class ReportTaskRowUi(
    val title: String,
    val isFinished: Boolean,
    val subtaskCount: Int,
    val intervalCount: Int,
    val shareFraction: Float,
    /** "44%". */
    val sharePercent: String,
    /** "03:52:39". */
    val duration: String,
)

internal fun ProjectReport.toProjectReportUi(): ProjectReportUi =
    ProjectReportUi(
        title = title,
        description = description?.takeIf { it.isNotBlank() },
        accent = colorArgb?.let(::Color) ?: reportDefaultAccent,
        colorHex = colorArgb?.let { "#" + (it and RGB_MASK).toString(HEX_RADIX).uppercase().padStart(HEX_DIGITS, '0') },
        statusLabel = status.labelRes(),
        exportedDate = formatReportDate(exportedAt),
        summary = summary.toReportSummaryUi(),
        taskRows = tasks.map { it.toReportTaskRowUi() },
        months = months.toReportMonthUis(),
        taskSections = tasks.map { it.toReportTaskSectionUi() },
    )

internal fun ProjectStatus.labelRes(): StringResource =
    when (this) {
        ProjectStatus.ACTIVE -> Res.string.report_status_active
        ProjectStatus.FINISHED -> Res.string.report_status_finished
        ProjectStatus.ARCHIVED -> Res.string.report_status_archived
        ProjectStatus.TRASHED -> Res.string.report_status_trashed
    }

internal fun taskStatusLabelRes(isFinished: Boolean): StringResource =
    if (isFinished) Res.string.report_status_finished else Res.string.report_status_open

private fun ReportSummary.toReportSummaryUi() =
    ReportSummaryUi(
        totalTracked = formatReportDuration(totalTracked),
        taskCount = taskCount,
        finishedTaskCount = finishedTaskCount,
        openTaskCount = openTaskCount,
        intervalCount = intervalCount,
        activeDays = activeDays,
        activeDateRange =
            if (firstActiveDate != null && lastActiveDate != null) {
                formatReportDateRange(firstActiveDate, lastActiveDate)
            } else {
                null
            },
    )

private fun ReportTask.toReportTaskRowUi() =
    ReportTaskRowUi(
        title = title,
        isFinished = isFinished,
        subtaskCount = subtasks.size,
        intervalCount = intervals.size,
        shareFraction = shareFraction,
        sharePercent = formatSharePercent(shareFraction),
        duration = formatReportDuration(duration),
    )
