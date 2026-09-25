package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.core.presentation.pdf.PdfDocumentScope
import com.jvcs.tracky.core.presentation.pdf.PdfPageSpec
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.report_column_subtasks
import tracky.composeapp.generated.resources.report_heading_per_day
import tracky.composeapp.generated.resources.report_heading_tasks
import tracky.composeapp.generated.resources.report_intervals

private val SideMargin = 45.dp
private val SectionGap = 22.dp
private val TaskGap = 26.dp
private val LabelGap = 11.dp

/** The report's paper: A4 portrait with the design's margins; the footer sits close to the bottom edge. */
internal val ProjectReportPageSpec =
    PdfPageSpec(margins = PaddingValues(start = SideMargin, top = 45.dp, end = SideMargin, bottom = 20.dp))

/**
 * The whole project report, laid out on [ProjectReportPageSpec]: page one carries the header,
 * summary, task table and per-day calendars; the tasks then follow from a fresh page with their
 * subtasks and intervals, whose column header repeats atop every page the table continues onto.
 */
internal fun PdfDocumentScope.projectReportDocument(report: ProjectReportUi) {
    val accent = report.accent
    footer {
        ReportTheme(accent) { ReportPageFooter(report.title, report.exportedDate, ruleBleed = SideMargin) }
    }
    // Page one only, so an item rather than the repeating header.
    reportItem(accent) { ReportPageHeader(report.exportedDate) }
    reportItem(accent) {
        ReportProjectHeading(
            title = report.title,
            description = report.description,
            colorHex = report.colorHex,
            statusLabel = report.statusLabel,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
    reportItem(accent) { ReportSummaryCards(report.summary, Modifier.padding(top = 20.dp)) }

    reportItem(accent) {
        ReportSectionHeading(stringResource(Res.string.report_heading_tasks), Modifier.padding(top = SectionGap))
    }
    reportItem(accent) { ReportTasksTableHeader(Modifier.padding(top = 12.dp)) }
    report.taskRows.forEach { row -> reportItem(accent) { ReportTaskRow(row) } }
    reportItem(accent) { ReportTasksTotalRow(report.summary.intervalCount, report.summary.totalTracked) }

    if (report.months.isNotEmpty()) {
        reportItem(accent) {
            ReportSectionHeading(stringResource(Res.string.report_heading_per_day), Modifier.padding(top = SectionGap))
        }
        // One block per row of months, so pages break between rows.
        report.months.chunked(MONTHS_PER_ROW).forEachIndexed { index, months ->
            reportItem(accent) { ReportCalendarRow(months, Modifier.padding(top = if (index == 0) 8.dp else 16.dp)) }
        }
    }

    pageBreak()
    report.taskSections.forEachIndexed { index, section ->
        taskSection(section, accent, topGap = if (index == 0) 0.dp else TaskGap)
    }
}

private fun PdfDocumentScope.taskSection(
    section: ReportTaskSectionUi,
    accent: Color,
    topGap: Dp,
) {
    reportItem(accent) { ReportTaskSectionHeading(section, Modifier.padding(top = topGap)) }
    section.subtasks.forEachIndexed { index, subtask ->
        reportItem(accent) {
            if (index == 0) {
                ReportTableLabel(
                    stringResource(Res.string.report_column_subtasks),
                    Modifier.padding(top = LabelGap, bottom = 2.dp),
                )
            }
            ReportSubtaskRow(subtask)
        }
    }
    if (section.intervals.isEmpty()) return

    // The label opens the table once; the paginator draws the column header between it and the
    // first row, and again atop every page the rows continue onto.
    reportItem(accent) {
        ReportTableLabel(stringResource(Res.string.report_intervals), Modifier.padding(top = LabelGap, bottom = 4.dp))
    }
    section(repeatingHeader = { ReportTheme(accent) { ReportIntervalsTableHeader() } }) {
        section.intervals.forEach { interval -> reportItem(accent) { ReportIntervalRow(interval) } }
    }
}

/** Each block is composed on its own, so each brings the report theme along. */
private fun PdfDocumentScope.reportItem(accent: Color, content: @Composable () -> Unit) =
    item { ReportTheme(accent, content) }
