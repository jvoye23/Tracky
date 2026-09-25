// Report previews wrap in ReportTheme, the light-only print theme, not the app's TrackyTheme.
@file:Suppress("PreviewMustWrapInTheme")

package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.app_name
import tracky.composeapp.generated.resources.report_dot_separated
import tracky.composeapp.generated.resources.report_exported_on
import tracky.composeapp.generated.resources.report_project_report
import tracky.composeapp.generated.resources.tracky_icon

/** The top of page one: the Tracky mark and "PROJECT REPORT" on the left, the export date on the right. */
@Composable
internal fun ReportPageHeader(exportedDate: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // The icon is a gradient disc; drawn oversized and clipped, it reads as a rounded tile.
        Image(
            painter = painterResource(Res.drawable.tracky_icon),
            contentDescription = null,
            modifier =
                Modifier
                    .size(21.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .wrapContentSize(unbounded = true)
                    .size(33.dp),
        )
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            text = stringResource(Res.string.report_project_report),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.7.sp),
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        Text(
            text = exportedDate,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * Closes every page: "Tracky · <project>" on the left, the export date on the right, under a
 * hairline. The hairline reaches [ruleBleed] past both sides — the page margin, to run edge to edge.
 */
@Composable
internal fun ReportPageFooter(
    projectTitle: String,
    exportedDate: String,
    modifier: Modifier = Modifier,
    ruleBleed: Dp = 0.dp,
) {
    val ruleColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .drawBehind {
                    val stroke = 0.75.dp.toPx()
                    val ruleY = stroke / 2
                    val bleed = ruleBleed.toPx()
                    drawLine(ruleColor, Offset(-bleed, ruleY), Offset(size.width + bleed, ruleY), stroke)
                }.padding(top = 6.5.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text =
                    stringResource(
                        Res.string.report_dot_separated,
                        stringResource(Res.string.app_name),
                        projectTitle,
                    ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.report_exported_on, exportedDate),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** A section title such as "Tasks". */
@Composable
internal fun ReportSectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportPageHeaderPreview() {
    ReportTheme {
        ReportPageHeader(exportedDate = "22 Sept 2026")
    }
}

@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportPageFooterPreview() {
    ReportTheme {
        ReportPageFooter(projectTitle = "API Testing update", exportedDate = "22 Sept 2026")
    }
}

/** A title too long for the line is cut short, so the export date never gets pushed off the page. */
@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportPageFooterLongTitlePreview() {
    ReportTheme {
        ReportPageFooter(
            projectTitle = "Quarterly planning and cross-team API migration for the mobile platform",
            exportedDate = "22 Sept 2026",
        )
    }
}

@Preview(widthDp = 515, showBackground = true)
@Composable
private fun ReportSectionHeadingPreview() {
    ReportTheme {
        ReportSectionHeading(text = "Tasks")
    }
}
