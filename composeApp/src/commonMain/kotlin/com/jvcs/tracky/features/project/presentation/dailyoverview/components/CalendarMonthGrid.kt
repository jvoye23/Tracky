package com.jvcs.tracky.features.project.presentation.dailyoverview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.jvcs.tracky.designsystem.theme.SampleProjectColors
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.util.formatDurationHoursMinutes
import com.jvcs.tracky.features.project.presentation.models.CalendarDayUi
import com.jvcs.tracky.features.project.presentation.models.CalendarMonthUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.calendar_day_description
import tracky.composeapp.generated.resources.calendar_day_untracked
import kotlin.time.Duration.Companion.milliseconds

/** Above this luminance, black text reads better than white on the tint. */
private const val DARK_TEXT_LUMINANCE_THRESHOLD = 0.18f

/** Tint alpha of the least busy tracked day. Matches the "Per day" strip's ramp. */
internal const val MIN_INTENSITY = 0.22f

/** Tint alpha just below the busiest day, which always renders fully opaque. */
internal const val MAX_INTENSITY = 0.55f

internal const val DAYS_PER_WEEK = 7

/**
 * Black or white, whichever stays readable on a surface filled with the project colour.
 *
 * A project colour is user-picked, so it cannot borrow a content colour from the theme - a bright
 * one stays bright in dark mode. Composite the tint over the card first, then split at 0.18
 * luminance, which is where black and white swap places on the WCAG contrast curve.
 */
@Composable
internal fun onProjectColor(tint: Color): Color {
    val composited = tint.compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    return if (composited.luminance() > DARK_TEXT_LUMINANCE_THRESHOLD) Color.Black else Color.White
}

@Composable
internal fun CalendarMonthGrid(
    month: CalendarMonthUi,
    selectedDate: LocalDate?,
    projectColor: Color,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A plain Column of Rows, not a LazyVerticalGrid: a month is at most six rows, all of them on
    // screen at once, so laziness would only add nested-scroll trouble inside the outer list.
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        month.days.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        maxTrackedMillis = month.maxTrackedMillis,
                        isSelected = day.date == selectedDate,
                        projectColor = projectColor,
                        onClick = { onDateSelected(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDayUi,
    maxTrackedMillis: Long,
    isSelected: Boolean,
    projectColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A cell from a neighbouring month holds the grid's shape and draws nothing else. Every page
    // is six weeks, so a short month would otherwise finish on a whole row of another month's
    // numbers - which, at a glance, reads as belonging to the month on screen.
    if (!day.isInMonth) {
        Box(modifier = modifier.aspectRatio(1f))
        return
    }

    val intensity: Float? =
        when {
            day.trackedMillis <= 0L || maxTrackedMillis <= 0L -> null
            day.trackedMillis >= maxTrackedMillis -> 1f
            else -> lerp(MIN_INTENSITY, MAX_INTENSITY, day.trackedMillis.toFloat() / maxTrackedMillis)
        }

    val background =
        if (intensity != null) {
            projectColor.copy(alpha = intensity)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    val onCell =
        if (intensity != null) {
            onProjectColor(background)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    val description =
        stringResource(
            Res.string.calendar_day_description,
            day.dayLabel,
            day.trackedMillis
                .takeIf { it > 0L }
                ?.let { formatDurationHoursMinutes(it.milliseconds) }
                ?: stringResource(Res.string.calendar_day_untracked),
        )

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(background)
                .then(
                    when {
                        isSelected -> Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        day.isToday -> Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        else -> Modifier
                    },
                ).clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.dayLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = onCell,
            )
            // The dot repeats what the tint already says, for anyone who cannot separate the
            // tints by eye. An untracked day keeps the space so the numbers stay on one baseline.
            Box(
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(if (intensity != null) onCell.copy(alpha = 0.6f) else Color.Transparent),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val PreviewProjectColor = SampleProjectColors.Orange
private val PreviewAlternateProjectColor = SampleProjectColors.Teal

/**
 * September 2026, drawn from the design reference: opens on a Tuesday, so one leading padding
 * cell, and the busiest day is Tue 08 at full tint.
 */

/** Shared with [CalendarMonthCard]'s previews, so the fixture is written once. */
@Suppress("MagicNumber") // preview fixture: sample data is meant to be literal
internal fun previewCalendarMonth(
    tracked: Map<Int, Long> =
        mapOf(
            1 to 42L,
            2 to 38L,
            4 to 95L,
            5 to 24L,
            7 to 78L,
            8 to 206L,
            9 to 31L,
            11 to 52L,
            12 to 64L,
            15 to 18L,
            16 to 71L,
            18 to 45L,
            21 to 33L,
            22 to 68L,
            25 to 40L,
            29 to 57L,
        ),
    today: LocalDate = LocalDate(2026, 9, 9),
): CalendarMonthUi {
    val month = YearMonth(2026, 9)
    // One leading cell (Mon 31 Aug) then the 30 days, padded out to six whole weeks the way the
    // mapper does — the preview has to show the same fixed height the pager relies on.
    val gridStart = LocalDate(2026, 8, 31)
    val days =
        (0 until 42).map { offset ->
            val date = LocalDate.fromEpochDays(gridStart.toEpochDays() + offset)
            val inMonth = date.year == 2026 && date.month == month.month
            CalendarDayUi(
                date = date,
                dayLabel = date.day.toString().padStart(2, '0'),
                trackedMillis = if (inMonth) (tracked[date.day] ?: 0L) * 60_000L else 0L,
                isToday = date == today,
                isInMonth = inMonth,
            )
        }
    return CalendarMonthUi(
        yearMonth = month,
        monthLabel = "September 2026",
        monthTotalLabel = "20:08",
        days = days,
        busiestDayLabel = "Tue 08",
        maxTrackedMillis = days.maxOf { it.trackedMillis },
    )
}

@Composable
private fun GridPreviewContainer(content: @Composable () -> Unit) {
    TrackyTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@PreviewLightDark
@Composable
private fun CalendarMonthGridDefaultPreview() {
    GridPreviewContainer {
        CalendarMonthGrid(
            month = previewCalendarMonth(),
            selectedDate = LocalDate(2026, 9, 8),
            projectColor = PreviewProjectColor,
            onDateSelected = {},
        )
    }
}

/** Nothing tracked: every in-month cell falls back to the surface tint, and no dots show. */
@PreviewLightDark
@Composable
private fun CalendarMonthGridEmptyPreview() {
    GridPreviewContainer {
        CalendarMonthGrid(
            month = previewCalendarMonth(tracked = emptyMap()).copy(monthTotalLabel = null, busiestDayLabel = null),
            selectedDate = LocalDate(2026, 9, 8),
            projectColor = PreviewProjectColor,
            onDateSelected = {},
        )
    }
}

/** One tracked day is also the busiest, so it renders at full opacity on its own. */
@PreviewLightDark
@Composable
private fun CalendarMonthGridSingleTrackedDayPreview() {
    GridPreviewContainer {
        CalendarMonthGrid(
            month = previewCalendarMonth(tracked = mapOf(8 to 27L)),
            selectedDate = LocalDate(2026, 9, 8),
            projectColor = PreviewProjectColor,
            onDateSelected = {},
        )
    }
}

@Preview(name = "Alternate project colour")
@Composable
private fun CalendarMonthGridAlternateColorPreview() {
    GridPreviewContainer {
        CalendarMonthGrid(
            month = previewCalendarMonth(),
            selectedDate = LocalDate(2026, 9, 8),
            projectColor = PreviewAlternateProjectColor,
            onDateSelected = {},
        )
    }
}

@Preview(name = "Compact", widthDp = 320)
@Composable
private fun CalendarMonthGridCompactPreview() {
    GridPreviewContainer {
        CalendarMonthGrid(
            month = previewCalendarMonth(),
            selectedDate = LocalDate(2026, 9, 8),
            projectColor = PreviewProjectColor,
            onDateSelected = {},
        )
    }
}

@Preview(name = "Expanded width", widthDp = 840)
@Composable
private fun CalendarMonthGridExpandedWidthPreview() {
    GridPreviewContainer {
        CalendarMonthGrid(
            month = previewCalendarMonth(),
            selectedDate = LocalDate(2026, 9, 8),
            projectColor = PreviewProjectColor,
            onDateSelected = {},
        )
    }
}

/** The cells are square and sized by width, so large text must not overflow them. */
@Preview(name = "Font scale 2x", fontScale = 2f)
@Composable
private fun CalendarMonthGridFontScalePreview() {
    GridPreviewContainer {
        CalendarMonthGrid(
            month = previewCalendarMonth(),
            selectedDate = LocalDate(2026, 9, 8),
            projectColor = PreviewProjectColor,
            onDateSelected = {},
        )
    }
}
