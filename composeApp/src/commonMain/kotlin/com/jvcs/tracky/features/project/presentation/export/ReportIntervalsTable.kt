// Report previews wrap in ReportTheme, the light-only print theme, not the app's TrackyTheme.
@file:Suppress("PreviewMustWrapInTheme")

package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.report_column_date
import tracky.composeapp.generated.resources.report_column_duration
import tracky.composeapp.generated.resources.report_column_start_end
import tracky.composeapp.generated.resources.report_column_subtask

/** Column widths of the intervals table, as weights shared by its header and rows. */
private object IntervalColumns {

    const val DATE = 93f
    const val TIME = 109f
    const val SUBTASK = 233f
    const val DURATION = 70f
}

private val IntervalRowPadding = 2.5.dp

/** The intervals table's column captions; the PDF repeats it atop every page the table continues onto. */
@Composable
internal fun ReportIntervalsTableHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(bottom = 4.dp)) {
            Caption(stringResource(Res.string.report_column_date), IntervalColumns.DATE)
            Caption(stringResource(Res.string.report_column_start_end), IntervalColumns.TIME)
            Caption(stringResource(Res.string.report_column_subtask), IntervalColumns.SUBTASK)
            Caption(stringResource(Res.string.report_column_duration), IntervalColumns.DURATION, TextAlign.End)
        }
        HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** One interval: day, clock range, the subtasks timed inside it and its duration. */
@Composable
internal fun ReportIntervalRow(interval: ReportIntervalRowUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = IntervalRowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val mono = MaterialTheme.typography.labelLarge
            Cell(interval.date, IntervalColumns.DATE, mono)
            Cell(interval.time, IntervalColumns.TIME, mono)
            Text(
                text = interval.subtasks,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(IntervalColumns.SUBTASK).padding(end = 8.dp),
            )
            Cell(interval.duration, IntervalColumns.DURATION, MaterialTheme.typography.displaySmall, TextAlign.End)
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun RowScope.Caption(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    style: TextStyle,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}

/** Rows without a subtask show a dash; too many subtask names are cut short before the duration. */
@Preview(widthDp = 505, showBackground = true)
@Composable
private fun ReportIntervalsTablePreview() {
    ReportTheme {
        Column {
            ReportIntervalsTableHeader()
            SampleIntervals.forEach { ReportIntervalRow(interval = it) }
        }
    }
}

private val SampleIntervals =
    listOf(
        ReportIntervalRowUi("Fri 14 Aug", "06:58 – 06:58", "—", "00:00:54"),
        ReportIntervalRowUi("Wed 26 Aug", "13:21 – 14:53", "Subtask Number One, subtask update", "01:32:00"),
        ReportIntervalRowUi(
            date = "Wed 02 Sept",
            time = "14:06 – 23:51",
            subtasks = "Cross Device Subtask 1, hfthh, gggjjjjjj, Subtask Number One, subtask update, New Test",
            duration = "09:45:07",
        ),
    )
