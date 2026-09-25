// Report previews wrap in ReportTheme, the light-only print theme, not the app's TrackyTheme.
@file:Suppress("PreviewMustWrapInTheme")

package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.designsystem.theme.SampleProjectColors
import com.jvcs.tracky.features.project.domain.models.ProjectStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.report_active_days
import tracky.composeapp.generated.resources.report_dot_separated
import tracky.composeapp.generated.resources.report_intervals
import tracky.composeapp.generated.resources.report_task_breakdown
import tracky.composeapp.generated.resources.report_tasks
import tracky.composeapp.generated.resources.report_total_tracked

/** The project's color dot and code with its status, then the title and, when there is one, the description. */
@Composable
internal fun ReportProjectHeading(
    title: String,
    description: String?,
    colorHex: String?,
    statusLabel: StringResource,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            val status = stringResource(statusLabel)
            Text(
                text = colorHex?.let { stringResource(Res.string.report_dot_separated, it, status) } ?: status,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Four bordered figures in one strip: total tracked in the accent, then tasks, intervals and active days. */
@Composable
internal fun ReportSummaryCards(summary: ReportSummaryUi, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        val cell = Modifier.weight(1f).fillMaxHeight()
        SummaryCell(
            label = stringResource(Res.string.report_total_tracked),
            value = summary.totalTracked,
            valueStyle = MaterialTheme.typography.displayMedium,
            valueColor = MaterialTheme.colorScheme.primary,
            modifier = cell,
        )

        CellDivider()
        SummaryCell(
            label = stringResource(Res.string.report_tasks),
            value = summary.taskCount.toString(),
            note =
                stringResource(Res.string.report_task_breakdown, summary.finishedTaskCount, summary.openTaskCount),
            modifier = cell,
        )

        CellDivider()
        SummaryCell(
            label = stringResource(Res.string.report_intervals),
            value = summary.intervalCount.toString(),
            modifier = cell,
        )

        CellDivider()
        SummaryCell(
            label = stringResource(Res.string.report_active_days),
            value = summary.activeDays.toString(),
            note = summary.activeDateRange,
            modifier = cell,
        )
    }
}

@Composable
private fun CellDivider() {
    VerticalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SummaryCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    valueStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = valueStyle, color = valueColor, modifier = Modifier.padding(top = 4.dp))
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportProjectHeadingPreview() {
    ReportTheme {
        ReportProjectHeading(
            title = "API Testing update",
            description = "this a new description update",
            colorHex = "#06D9E5",
            statusLabel = ProjectStatus.ACTIVE.labelRes(),
        )
    }
}

/** No color of its own: the dot takes the default accent and the code is left out; no description either. */
@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportProjectHeadingPlainPreview() {
    ReportTheme {
        ReportProjectHeading(
            title = "Kitchen renovation planning and supplier coordination",
            description = null,
            colorHex = null,
            statusLabel = ProjectStatus.FINISHED.labelRes(),
        )
    }
}

@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportSummaryCardsPreview() {
    ReportTheme {
        ReportSummaryCards(
            summary =
                ReportSummaryUi(
                    totalTracked = "08:46:20",
                    taskCount = 5,
                    finishedTaskCount = 2,
                    openTaskCount = 3,
                    intervalCount = 92,
                    activeDays = 15,
                    activeDateRange = "14 Aug 2026 – 22 Sept 2026",
                ),
        )
    }
}

/** Nothing tracked yet: zero figures and no date range under the active days. */
@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportSummaryCardsEmptyPreview() {
    ReportTheme(accent = SampleProjectColors.Orange) {
        ReportSummaryCards(
            summary =
                ReportSummaryUi(
                    totalTracked = "00:00:00",
                    taskCount = 0,
                    finishedTaskCount = 0,
                    openTaskCount = 0,
                    intervalCount = 0,
                    activeDays = 0,
                    activeDateRange = null,
                ),
        )
    }
}
