// Report previews wrap in ReportTheme, the light-only print theme, not the app's TrackyTheme.
@file:Suppress("PreviewMustWrapInTheme")

package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvcs.tracky.designsystem.theme.SampleProjectColors
import org.jetbrains.compose.resources.stringArrayResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.report_weekday_initials

/** Month cards per row of the "Per day" section. */
internal const val MONTHS_PER_ROW = 2

private const val MIN_TINT = 0.15f
private const val TINT_RANGE = 0.4f
private val CellGap = 2.dp

/**
 * One row of the "Per day" heat map: up to [MONTHS_PER_ROW] month cards of equal height side by
 * side; a lone month keeps its half width. One row is one PDF block, so pages break between rows.
 */
@Composable
internal fun ReportCalendarRow(months: List<ReportMonthUi>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(MONTHS_PER_ROW) { index ->
            val month = months.getOrNull(index)
            if (month != null) {
                MonthCard(month = month, modifier = Modifier.weight(1f).fillMaxHeight())
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MonthCard(month: ReportMonthUi, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .padding(horizontal = 11.5.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(CellGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = month.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = month.total,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(Modifier.padding(top = 6.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
            stringArrayResource(Res.array.report_weekday_initials).forEach { initial ->
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        month.weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                week.forEach { day ->
                    if (day != null) DayTile(day, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Untracked days are a grey tile; tracked ones tint with their intensity; the peak day is solid. */
@Composable
private fun DayTile(day: ReportDayUi, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tracked = day.duration != null
    val background =
        when {
            day.isPeak -> colors.primary
            tracked -> colors.primary.copy(alpha = MIN_TINT + TINT_RANGE * day.intensity)
            else -> colors.surfaceContainerLow
        }
    val content =
        when {
            day.isPeak -> colors.onPrimary
            tracked -> colors.onSurface
            else -> colors.outline
        }
    Column(
        modifier = modifier.height(22.dp).background(background, RoundedCornerShape(4.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val style = MaterialTheme.typography.labelLarge
        Text(
            text = day.dayOfMonth,
            style = style.copy(fontSize = 7.5.sp, fontWeight = if (tracked) FontWeight.Bold else FontWeight.Normal),
            color = content,
        )
        if (day.duration != null) {
            Text(
                text = day.duration,
                style = style.copy(fontSize = 6.sp, fontWeight = FontWeight.Medium),
                color = content,
            )
        }
    }
}

@Preview(widthDp = 505, showBackground = true)
@Composable
private fun ReportCalendarRowPreview() {
    ReportTheme {
        ReportCalendarRow(months = listOf(SampleAugust, SampleSeptember))
    }
}

/** An odd month out keeps half the width rather than stretching across the row. */
@Preview(widthDp = 505, showBackground = true)
@Composable
private fun ReportCalendarRowSingleMonthPreview() {
    ReportTheme(accent = SampleProjectColors.Orange) {
        ReportCalendarRow(months = listOf(SampleSeptember))
    }
}

private val SampleAugust =
    sampleMonth(
        title = "August 2026",
        total = "01:42:48",
        leading = 5,
        length = 31,
        tracked =
            mapOf(
                14 to "0h04",
                16 to "0h01",
                17 to "0h00",
                24 to "0h00",
                25 to "0h04",
                26 to "1h34",
                27 to "0h00",
            ),
        intensities = mapOf(26 to 0.83f),
    )

private val SampleSeptember =
    sampleMonth(
        title = "September 2026",
        total = "02:24:57",
        leading = 1,
        length = 30,
        tracked = mapOf(2 to "0h00", 8 to "1h52", 10 to "0h02", 13 to "0h01", 17 to "0h24", 22 to "0h04"),
        intensities = mapOf(8 to 1f, 17 to 0.21f),
    )

private fun sampleMonth(
    title: String,
    total: String,
    leading: Int,
    length: Int,
    tracked: Map<Int, String>,
    intensities: Map<Int, Float>,
): ReportMonthUi {
    val days =
        (1..length).map { day ->
            ReportDayUi(
                dayOfMonth = day.toString().padStart(2, '0'),
                duration = tracked[day],
                intensity = intensities[day] ?: 0f,
                isPeak = intensities[day] == 1f,
            )
        }
    return ReportMonthUi(title, total, days.toMondayFirstWeeks(leading))
}
