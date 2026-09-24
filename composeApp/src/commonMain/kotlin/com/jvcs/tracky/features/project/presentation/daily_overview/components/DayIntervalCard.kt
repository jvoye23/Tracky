package com.jvcs.tracky.features.project.presentation.daily_overview.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.theme.monoLabelSmall
import com.jvcs.tracky.designsystem.theme.monoLabelXSmall
import com.jvcs.tracky.features.project.presentation.models.DayDetailUi
import com.jvcs.tracky.features.project.presentation.models.DayIntervalUi
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.day_empty
import tracky.composeapp.generated.resources.day_interval_count
import tracky.composeapp.generated.resources.day_interval_count_single
import tracky.composeapp.generated.resources.day_interval_description
import tracky.composeapp.generated.resources.day_total

/**
 * The heading above the day's cards: the date, the day's total, and what the total is made of.
 */
@Composable
fun DayHeaderRow(day: DayDetailUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = day.dateLabel,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (day.intervalCount > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = stringResource(Res.string.day_total),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = day.totalDuration,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.padding(top = 2.dp))

        Text(
            text =
                if (day.intervalCount == 1 && day.taskCount == 1) {
                    stringResource(Res.string.day_interval_count_single, day.intervalCount, day.taskCount)
                } else {
                    stringResource(Res.string.day_interval_count, day.intervalCount, day.taskCount)
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * One interval, drawn as the design has it: a running number, the task that owns the time, the
 * stretch of clock it covers, and the duration on the right.
 *
 * The subtask, when there is one, shares the second line with the time range in a lighter colour
 * — it qualifies the interval rather than titling it.
 */
@Composable
fun DayIntervalCard(
    interval: DayIntervalUi,
    modifier: Modifier = Modifier,
    projectColor: Color,
) {
    val description =
        stringResource(
            Res.string.day_interval_description,
            interval.taskTitle,
            interval.timeRangeLabel,
            interval.formattedDuration,
        )

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = description },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border =
            BorderStroke(
                1.dp,
                projectColor.copy(alpha = 0.2f),
            ),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = interval.indexLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = interval.taskTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = interval.timeRangeLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    interval.subTaskTitle?.let { subTask ->
                        Text(
                            text = " · $subTask",
                            style = MaterialTheme.typography.titleMedium,
                            // Lighter than the range: the subtask qualifies the interval, it does
                            // not title it.
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = interval.formattedDuration,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Shown in place of the cards on a day that banked nothing. The heading stays either way. */
@Composable
fun DayEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.day_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val PreviewProjectColor = Color(0xFF475D92)

@Suppress("MagicNumber") // preview fixture: sample data is meant to be literal
private fun previewInterval(
    index: String = "01",
    task: String = "Design review",
    subTask: String? = "Calendar spec",
    range: String = "09:30 – 10:12",
    duration: String = "00:42:11",
    projectColor: Color = Color(0xFF475D92),
) = DayIntervalUi("id-$index", index, task, subTask, range, duration, projectColor)

@Composable
private fun DayPreviewContainer(content: @Composable () -> Unit) {
    TrackyTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = { content() },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DayListPreview() {
    DayPreviewContainer {
        DayHeaderRow(
            DayDetailUi(
                dateLabel = "Tue, Sep 08",
                headlineLabel = "Sep 8, 2026",
                totalDuration = "03:26:58",
                intervals = List(4) { previewInterval(index = "0${it + 1}") },
                taskCount = 4,
            ),
        )
        DayIntervalCard(
            previewInterval(
                index = "02",
                task = "Onboarding copy",
                subTask = null,
                range = "10:20 – 10:58",
                duration = "00:38:22",
            ),
            projectColor = PreviewProjectColor,
        )
        DayIntervalCard(
            previewInterval(
                index = "03",
                task = "Auth endpoints",
                subTask = "Token refresh",
                range = "13:15 – 14:47",
                duration = "01:32:07",
            ),
            projectColor = PreviewProjectColor,
        )
        DayIntervalCard(
            previewInterval(
                index = "04",
                task = "Testing Tasks",
                subTask = null,
                range = "15:30 – 16:04",
                duration = "00:34:18",
            ),
            projectColor = PreviewProjectColor,
        )
    }
}

/** A day with nothing on it keeps its heading and says so. */
@PreviewLightDark
@Composable
private fun DayEmptyPreview() {
    DayPreviewContainer {
        DayHeaderRow(DayDetailUi("Wed, Sep 09", "Sep 9, 2026", "00:00:00", emptyList(), 0))
        DayEmptyState()
    }
}

/** Singular wording, so one interval does not read as "1 intervals". */
@Preview(name = "Single interval")
@Composable
private fun DaySingleIntervalPreview() {
    DayPreviewContainer {
        DayHeaderRow(DayDetailUi("Thu, Sep 10", "Sep 10, 2026", "00:42:11", listOf(previewInterval()), 1))

        DayIntervalCard(
            previewInterval(range = "23:40 – 00:20", duration = "00:40:13", subTask = null),
            projectColor = PreviewProjectColor,
        )
    }
}

/** Both titles have to give way before the duration does. */
@Preview(name = "Long titles")
@Composable
private fun DayLongTitlesPreview() {
    DayPreviewContainer {
        DayIntervalCard(
            previewInterval(
                task = "Rework the onboarding flow copy for the returning-user case",
                subTask = "Second pass over the empty-state wording",
                projectColor = PreviewProjectColor,
            ),
            projectColor = PreviewProjectColor,
        )
    }
}

/** An interval running past midnight reads backwards; that is correct, not a bug. */
@Preview(name = "Past midnight")
@Composable
private fun DayPastMidnightPreview() {
    DayPreviewContainer {
        DayIntervalCard(
            previewInterval(range = "23:40 – 00:20", duration = "00:40:13", subTask = null),
            projectColor = PreviewProjectColor,
        )
    }
}

@Preview(name = "Compact", widthDp = 320)
@Composable
private fun DayCompactPreview() {
    DayPreviewContainer {
        DayHeaderRow(DayDetailUi("Tue, Sep 08", "Sep 8, 2026", "03:26:58", List(4) { previewInterval() }, 4))

        DayIntervalCard(
            previewInterval(range = "23:40 – 00:20", duration = "00:40:13", subTask = null),
            projectColor = PreviewProjectColor,
        )
    }
}

@Preview(name = "Font scale 2x", fontScale = 2f)
@Composable
private fun DayFontScalePreview() {
    DayPreviewContainer {
        DayHeaderRow(DayDetailUi("Tue, Sep 08", "Sep 8, 2026", "03:26:58", List(4) { previewInterval() }, 4))

        DayIntervalCard(
            previewInterval(range = "23:40 – 00:20", duration = "00:40:13", subTask = null),
            projectColor = PreviewProjectColor,
        )
    }
}
