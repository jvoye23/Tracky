// Report previews wrap in ReportTheme, the light-only print theme, not the app's TrackyTheme.
@file:Suppress("PreviewMustWrapInTheme")

package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.report_column_subtasks
import tracky.composeapp.generated.resources.report_dot_separated
import tracky.composeapp.generated.resources.report_interval_count
import tracky.composeapp.generated.resources.report_subtask_intervals
import tracky.composeapp.generated.resources.report_task_started

private val SubtaskStatusWidth = 40.dp
private val SubtaskIntervalsWidth = 42.dp
private val SubtaskDurationWidth = 61.dp

/** Opens a task's detail: title and duration, description, "Open · started … · 43 intervals", a thick rule. */
@Composable
internal fun ReportTaskSectionHeading(section: ReportTaskSectionUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 16.dp),
            )
            Text(
                text = section.duration,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (section.description != null) {
            Text(
                text = section.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        TaskMetaLine(section, Modifier.padding(top = 5.dp))
        HorizontalDivider(
            thickness = 0.75.dp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 7.5.dp),
        )
    }
}

/** "Open · started 14 Aug 2026 · 43 intervals". */
@Composable
private fun TaskMetaLine(section: ReportTaskSectionUi, modifier: Modifier = Modifier) {
    val status = stringResource(taskStatusLabelRes(section.isFinished))
    val started = stringResource(Res.string.report_task_started, section.period)
    val intervals =
        pluralStringResource(Res.plurals.report_interval_count, section.intervalCount, section.intervalCount)

    Text(
        text =
            stringResource(
                Res.string.report_dot_separated,
                stringResource(Res.string.report_dot_separated, status, started),
                intervals,
            ),
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 8.sp),
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier,
    )
}

/** A spaced-caps caption over a task's subtasks or intervals, such as "INTERVALS". */
@Composable
internal fun ReportTableLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier.fillMaxWidth(),
    )
}

/** One subtask: title, status, interval count as "21 int." and duration. */
@Composable
internal fun ReportSubtaskRow(subtask: ReportSubtaskRowUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 4.4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = subtask.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Text(
                text = stringResource(taskStatusLabelRes(subtask.isFinished)),
                style = MaterialTheme.typography.labelMedium,
                color = with(MaterialTheme.colorScheme) { if (subtask.isFinished) tertiary else outline },
                modifier = Modifier.width(SubtaskStatusWidth),
            )
            Text(
                text = stringResource(Res.string.report_subtask_intervals, subtask.intervalCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                modifier = Modifier.width(SubtaskIntervalsWidth),
            )
            Text(
                text = subtask.duration,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.width(SubtaskDurationWidth),
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Preview(widthDp = 505, showBackground = true)
@Composable
private fun ReportTaskSectionPreview() {
    ReportTheme {
        Column {
            ReportTaskSectionHeading(section = SampleSection)
            ReportTableLabel(stringResource(Res.string.report_column_subtasks), Modifier.padding(top = 10.dp))
            SampleSection.subtasks.forEach { ReportSubtaskRow(subtask = it) }
        }
    }
}

/** Finished, with no description: the meta line shows the whole period. */
@Preview(widthDp = 505, showBackground = true)
@Composable
private fun ReportTaskSectionHeadingFinishedPreview() {
    ReportTheme {
        ReportTaskSectionHeading(
            section =
                SampleSection.copy(
                    title = "iPhone Task",
                    description = null,
                    isFinished = true,
                    period = "14 Aug 2026 – 27 Aug 2026",
                    intervalCount = 1,
                    duration = "00:00:48",
                ),
        )
    }
}

@Preview(widthDp = 505, showBackground = true)
@Composable
private fun ReportSubtaskRowFinishedLongTitlePreview() {
    ReportTheme {
        ReportSubtaskRow(
            subtask =
                ReportSubtaskRowUi(
                    title = "Rewrite the whole onboarding flow for the new authentication endpoints and tokens",
                    isFinished = true,
                    intervalCount = 12,
                    duration = "124:05:31",
                ),
        )
    }
}

private val SampleSection =
    ReportTaskSectionUi(
        title = "Testing Tasks Update",
        description = "new description",
        isFinished = false,
        period = "14 Aug 2026",
        intervalCount = 43,
        duration = "04:21:39",
        subtasks =
            listOf(
                ReportSubtaskRowUi("Subtask Number One", isFinished = false, intervalCount = 21, duration = "05:30:48"),
                ReportSubtaskRowUi("New Test", isFinished = false, intervalCount = 0, duration = "00:00:00"),
            ),
        intervals = emptyList(),
    )
