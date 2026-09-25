// Report previews wrap in ReportTheme, the light-only print theme, not the app's TrackyTheme.
@file:Suppress("PreviewMustWrapInTheme")

package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.report_column_duration
import tracky.composeapp.generated.resources.report_column_share
import tracky.composeapp.generated.resources.report_column_status
import tracky.composeapp.generated.resources.report_column_subtasks
import tracky.composeapp.generated.resources.report_column_task
import tracky.composeapp.generated.resources.report_intervals
import tracky.composeapp.generated.resources.report_total

private val ShareStartPadding = 12.dp
private val SharePercentWidth = 26.dp
private val RowPadding = 7.dp

/** The column captions over a thick rule. */
@Composable
internal fun ReportTasksTableHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(bottom = RowPadding)) {
            Caption(stringResource(Res.string.report_column_task), ReportTaskColumns.TASK)
            Caption(stringResource(Res.string.report_column_status), ReportTaskColumns.STATUS)

            Caption(
                stringResource(Res.string.report_column_subtasks),
                ReportTaskColumns.SUBTASKS,
                align = TextAlign.End,
            )
            Caption(stringResource(Res.string.report_intervals), ReportTaskColumns.INTERVALS, align = TextAlign.End)

            Caption(
                text = stringResource(Res.string.report_column_share),
                weight = ReportTaskColumns.SHARE,
                modifier = Modifier.padding(start = ShareStartPadding),
            )
            Caption(
                stringResource(Res.string.report_column_duration),
                ReportTaskColumns.DURATION,
                align = TextAlign.End,
            )
        }
        HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** One task: bold title, status (green once finished), counts, its share of the total as a bar, its duration. */
@Composable
internal fun ReportTaskRow(task: ReportTaskRowUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = RowPadding), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(ReportTaskColumns.TASK).padding(end = RowPadding),
            )
            Text(
                text = stringResource(taskStatusLabelRes(task.isFinished)),
                style = MaterialTheme.typography.labelMedium,
                color = if (task.isFinished) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(ReportTaskColumns.STATUS),
            )

            NumberCell(task.subtaskCount.toString(), ReportTaskColumns.SUBTASKS)
            NumberCell(task.intervalCount.toString(), ReportTaskColumns.INTERVALS)
            Row(
                modifier = Modifier.weight(ReportTaskColumns.SHARE).padding(start = ShareStartPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShareBar(fraction = task.shareFraction, modifier = Modifier.weight(1f))
                Text(
                    text = task.sharePercent,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(SharePercentWidth),
                )
            }
            NumberCell(task.duration, ReportTaskColumns.DURATION, MaterialTheme.typography.displaySmall)
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The closing row: interval total and the tracked total in the accent. */
@Composable
internal fun ReportTasksTotalRow(
    intervalCount: Int,
    totalTracked: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.report_total),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(ReportTaskColumns.TASK + ReportTaskColumns.STATUS + ReportTaskColumns.SUBTASKS),
        )
        NumberCell(intervalCount.toString(), ReportTaskColumns.INTERVALS, MaterialTheme.typography.displaySmall)
        Box(Modifier.weight(ReportTaskColumns.SHARE))
        NumberCell(
            text = totalTracked,
            weight = ReportTaskColumns.DURATION,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RowScope.Caption(
    text: String,
    weight: Float,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.weight(weight).then(modifier),
    )
}

@Composable
private fun RowScope.NumberCell(
    text: String,
    weight: Float,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(text = text, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(weight))
}

@Composable
private fun ShareBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}

@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportTasksTablePreview() {
    ReportTheme {
        Column {
            ReportTasksTableHeader()
            SampleRows.forEach { ReportTaskRow(task = it) }
            ReportTasksTotalRow(intervalCount = 92, totalTracked = "08:46:20")
        }
    }
}

@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportTaskRowOpenPreview() {
    ReportTheme {
        ReportTaskRow(task = SampleRows[0])
    }
}

@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportTaskRowFinishedPreview() {
    ReportTheme {
        ReportTaskRow(task = SampleRows[3])
    }
}

/** A share too small to draw: the bar stays an empty track and the figure reads "0%". */
@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportTaskRowZeroSharePreview() {
    ReportTheme {
        ReportTaskRow(task = SampleRows[2].copy(isFinished = false, shareFraction = 0f))
    }
}

@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportTaskRowLongTitlePreview() {
    ReportTheme {
        ReportTaskRow(
            task =
                SampleRows[0].copy(
                    title = "Migrate the authentication flow to the new token endpoints",
                ),
        )
    }
}

/** The task rows of the project the export design was drawn from. */
private val SampleRows =
    listOf(
        sampleRow(
            title = "Testing Tasks Update",
            subtasks = 4,
            intervals = 43,
            share = 0.4983f,
            duration = "04:21:39",
        ),
        sampleRow(
            title = "Cross Device Test",
            subtasks = 3,
            intervals = 24,
            share = 0.0446f,
            duration = "00:23:29",
        ),
        sampleRow(
            title = "iPhone Task",
            subtasks = 0,
            intervals = 4,
            share = 0.0015f,
            duration = "00:00:48",
            done = true,
        ),
        sampleRow(
            title = "Task Number Four",
            subtasks = 0,
            intervals = 10,
            share = 0.4412f,
            duration = "03:52:39",
            done = true,
        ),
        sampleRow(title = "Neuer Task", subtasks = 0, intervals = 11, share = 0.0147f, duration = "00:07:44"),
    )

private fun sampleRow(
    title: String,
    subtasks: Int,
    intervals: Int,
    share: Float,
    duration: String,
    done: Boolean = false,
) = ReportTaskRowUi(
    title = title,
    isFinished = done,
    subtaskCount = subtasks,
    intervalCount = intervals,
    shareFraction = share,
    sharePercent = formatSharePercent(share),
    duration = duration,
)
