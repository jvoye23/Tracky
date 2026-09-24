package com.jvcs.tracky.features.project.presentation.project_detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.models.PerDayUi
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.duration_per_day
import tracky.composeapp.generated.resources.per_day_cell_description
import tracky.composeapp.generated.resources.per_day_cell_untracked
import tracky.composeapp.generated.resources.per_day_no_activity
import tracky.composeapp.generated.resources.per_day_summary

/** Placeholder glyph shown in a day cell with no tracked time. Decorative, not translatable. */
private const val UNTRACKED_GLYPH = "·"

/** Tint alpha of the least busy tracked day. */
private const val MIN_INTENSITY = 0.22f

/** Tint alpha just below the busiest day, which always renders fully opaque. */
private const val MAX_INTENSITY = 0.55f

/**
 * Width of a day tile. An `HH:mm:ss` duration measures ~55dp in `titleSmall`, so 20dp of
 * horizontal padding and a little headroom for a heavier weight land here. Sizing every tile to
 * the widest content it can hold keeps a tile carrying only the placeholder glyph exactly as
 * wide as a tracked one, so the strip reads as an even row.
 */
private val MIN_TILE_WIDTH = 80.dp

/**
 * "Per day" activity strip: one tile per day, tinted by how much time was tracked that
 * day relative to the busiest day in [days].
 *
 * Stateless — the caller supplies already-formatted labels, so a tile whose
 * [PerDayUi.formattedDuration] is `null` still renders as untracked even though the mapper
 * now feeds only active days. The strip scrolls horizontally because ten tiles do not fit a
 * phone at the design's fixed tile width.
 *
 * @param busiestDayLabel weekday plus date of the busiest day, carrying the month the
 * same way the tiles do (see [PerDayUi.dateLabel]), e.g. "Sat 05.9"; `null` when nothing
 * was tracked in the window.
 */
@Composable
fun PerDayCard(
    days: List<PerDayUi>,
    busiestDayLabel: String?,
    projectColor: Color,
    modifier: Modifier = Modifier,
    onDayClick: (LocalDate) -> Unit = {},
) {
    val maxMillis = days.maxOfOrNull { it.trackedMillis } ?: 0L

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.03f)),
    ) {
        Column(modifier = Modifier.padding(top = 10.dp, bottom = 11.dp)) {
            PerDayHeader(
                modifier = Modifier.padding(horizontal = 14.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(days) { day ->
                    DayCell(
                        day = day,
                        maxMillis = maxMillis,
                        projectColor = projectColor,
                        onClick = day.date?.let { date -> { onDayClick(date) } },
                    )
                }
            }

            Spacer(modifier = Modifier.height(9.dp))

            PerDayFooter(
                dayCount = days.size,
                busiestDayLabel = busiestDayLabel,
                projectColor = projectColor,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}

@Composable
private fun PerDayHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.duration_per_day).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun DayCell(
    day: PerDayUi,
    maxMillis: Long,
    projectColor: Color,
    modifier: Modifier = Modifier,
    // Null for a tile built without a date, which keeps the older previews inert.
    onClick: (() -> Unit)? = null,
) {
    val intensity: Float? =
        when {
            day.trackedMillis <= 0L || maxMillis <= 0L -> null
            day.trackedMillis >= maxMillis -> 1f
            else -> lerp(MIN_INTENSITY, MAX_INTENSITY, day.trackedMillis.toFloat() / maxMillis)
        }

    val isTracked = intensity != null
    val background =
        if (intensity != null) {
            projectColor.copy(alpha = intensity)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    // A tinted tile is filled with the project colour, so its content colour cannot come
    // from the theme — a bright project colour stays bright in dark mode. Composite the
    // tint over the card and pick black or white, the same way DurationHeroCard does.
    // 0.18 is where black and white swap places on the WCAG contrast-ratio curve.
    val tileColor = background.compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    val onTile = if (tileColor.luminance() > 0.18f) Color.Black else Color.White

    val description =
        stringResource(
            Res.string.per_day_cell_description,
            day.weekdayLabel,
            day.dateLabel,
            day.formattedDuration ?: stringResource(Res.string.per_day_cell_untracked),
        )

    Column(
        modifier =
            modifier
                .widthIn(min = MIN_TILE_WIDTH)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 16.dp)
                .padding(10.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = day.weekdayLabel,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (isTracked) {
                    onTile.copy(
                        alpha = 0.5f,
                    )
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
        )
        Text(
            text = day.dateLabel,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.1).sp,
                ),
            color = if (isTracked) onTile else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Text(
            text = day.formattedDuration ?: UNTRACKED_GLYPH,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color =
                if (isTracked) {
                    onTile.copy(
                        alpha = 0.72f,
                    )
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
        )
    }
}

@Composable
private fun PerDayFooter(
    dayCount: Int,
    busiestDayLabel: String?,
    projectColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                if (busiestDayLabel != null) {
                    stringResource(Res.string.per_day_summary, busiestDayLabel)
                } else {
                    stringResource(Res.string.per_day_no_activity, dayCount)
                },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IntensityLegend(projectColor = projectColor)
    }
}

@Composable
private fun IntensityLegend(projectColor: Color, modifier: Modifier = Modifier) {
    Row(
        // Purely a visual key for the tints above; nothing for a screen reader to announce.
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendSwatch(color = MaterialTheme.colorScheme.surfaceVariant)
        LegendSwatch(color = projectColor.copy(alpha = MIN_INTENSITY))
        LegendSwatch(color = projectColor.copy(alpha = 0.40f))
        LegendSwatch(color = projectColor)
    }
}

@Composable
private fun LegendSwatch(color: Color) {
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
    )
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val PreviewProjectColor = Color(0xFFF39B19)
private val PreviewAlternateProjectColor = Color(0xFF3E8E8A)

private fun minutes(value: Long): Long = value * 60_000L

private fun seconds(value: Long): Long = value * 1_000L

/**
 * A full strip: the ten most recent active days, drawn from the design reference. The dates skip
 * 26.8, 28.8, 29.8, 31.8, 3.9 and 4.9 the way the mapper does — an untracked day takes no tile.
 */
private fun referenceDays(): List<PerDayUi> =
    listOf(
        PerDayUi("Mon", "24.8", "00:52:12", minutes(52) + seconds(12)),
        PerDayUi("Tue", "25.8", "00:23:41", minutes(23) + seconds(41)),
        PerDayUi("Thu", "27.8", "00:41:07", minutes(41) + seconds(7)),
        PerDayUi("Sun", "30.8", "00:12:30", minutes(12) + seconds(30)),
        PerDayUi("Tue", "01.9", "00:35:00", minutes(35)),
        PerDayUi("Wed", "02.9", "00:19:55", minutes(19) + seconds(55)),
        PerDayUi("Sat", "05.9", "01:00:02", minutes(60) + seconds(2)),
        PerDayUi("Sun", "06.9", "00:08:44", minutes(8) + seconds(44)),
        PerDayUi("Tue", "08.9", "00:27:03", minutes(27) + seconds(3)),
        PerDayUi("Wed", "09.9", "00:44:20", minutes(44) + seconds(20)),
    )

/** The first day a project banks time: one tile, which is also the busiest. */
private fun singleTrackedDay(): List<PerDayUi> =
    listOf(
        PerDayUi("Wed", "09.9", "00:07:18", minutes(7) + seconds(18)),
    )

@Composable
private fun PerDayCardPreviewContainer(content: @Composable () -> Unit) {
    TrackyTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PerDayCardDefaultPreview() {
    PerDayCardPreviewContainer {
        PerDayCard(
            days = referenceDays(),
            busiestDayLabel = "Sat 05.9",
            projectColor = PreviewProjectColor,
        )
    }
}

@PreviewLightDark
@Composable
private fun PerDayCardSingleTrackedDayPreview() {
    PerDayCardPreviewContainer {
        PerDayCard(
            days = singleTrackedDay(),
            busiestDayLabel = "Wed 09.9",
            projectColor = PreviewProjectColor,
        )
    }
}

@PreviewLightDark
@Composable
private fun PerDayCardAlternateColorPreview() {
    PerDayCardPreviewContainer {
        PerDayCard(
            days = referenceDays(),
            busiestDayLabel = "Sat 05.9",
            projectColor = PreviewAlternateProjectColor,
        )
    }
}

@Preview(name = "Compact", widthDp = 320)
@Composable
private fun PerDayCardCompactPreview() {
    PerDayCardPreviewContainer {
        PerDayCard(
            days = referenceDays(),
            busiestDayLabel = "Sat 05.9",
            projectColor = PreviewProjectColor,
        )
    }
}

@Preview(name = "Expanded width", widthDp = 840)
@Composable
private fun PerDayCardExpandedWidthPreview() {
    PerDayCardPreviewContainer {
        PerDayCard(
            days = referenceDays(),
            busiestDayLabel = "Sat 05.9",
            projectColor = PreviewProjectColor,
        )
    }
}

@Preview(name = "Font scale 2x", fontScale = 2f)
@Composable
private fun PerDayCardFontScalePreview() {
    PerDayCardPreviewContainer {
        PerDayCard(
            days = referenceDays(),
            busiestDayLabel = "Sat 05.9",
            projectColor = PreviewProjectColor,
        )
    }
}
