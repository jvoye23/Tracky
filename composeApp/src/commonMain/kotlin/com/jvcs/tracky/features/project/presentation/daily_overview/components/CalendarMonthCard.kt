package com.jvcs.tracky.features.project.presentation.daily_overview.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.models.CalendarMonthUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DayOfWeekNames
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.calendar_intensity_less
import tracky.composeapp.generated.resources.calendar_jump_to_today
import tracky.composeapp.generated.resources.calendar_intensity_more
import tracky.composeapp.generated.resources.calendar_next_month
import tracky.composeapp.generated.resources.calendar_previous_month
import tracky.composeapp.generated.resources.calendar_select_date_title

/**
 * The calendar card: month title and running total, the weekday header, the grid, and a footer
 * naming the busiest day beside a key for the tints.
 *
 * Stateless, and paging is hoisted to the caller as [onPreviousMonth] / [onNextMonth] so that
 * swiping can replace the chevrons later without this card changing shape.
 */
@Composable
fun CalendarMonthCard(
    month: CalendarMonthUi,
    selectedDate: LocalDate?,
    selectedDateLabel: String?,
    projectColor: Color,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
    canGoBack: Boolean = true,
    canGoForward: Boolean = true
) {
    // Today is already flagged on its cell, so the header's jump-to-today needs no extra input
    // and no ViewModel round trip - selecting the date is the whole behaviour.
    val todayDate = month.monthDays.firstOrNull { it.isToday }?.date
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.03f))
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            MonthHeader(
                month = month,
                selectedDateLabel = selectedDateLabel,
                onJumpToToday = todayDate?.let { date -> { onDateSelected(date) } },
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            WeekdayHeaderRow(modifier = Modifier.padding(horizontal = 12.dp))

            Spacer(modifier = Modifier.height(4.dp))

            CalendarMonthGrid(
                month = month,
                selectedDate = selectedDate,
                projectColor = projectColor,
                onDateSelected = onDateSelected,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            MonthFooter(
                projectColor = projectColor,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }
    }
}

/**
 * Follows Material 3's date-picker header: a supporting title, the selected date as the
 * headline, then the month and its chevrons below a divider.
 *
 * @param onJumpToToday null when today is not on this page, which disables the action rather
 * than offering a jump that would go nowhere.
 */
@Composable
private fun MonthHeader(
    month: CalendarMonthUi,
    selectedDateLabel: String?,
    onJumpToToday: (() -> Unit)?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.calendar_select_date_title),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedDateLabel.orEmpty(),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onJumpToToday ?: {}, enabled = onJumpToToday != null) {
                Icon(
                    imageVector = Icons.Outlined.Today,
                    contentDescription = stringResource(Res.string.calendar_jump_to_today)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = month.monthLabel,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onPreviousMonth, enabled = canGoBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(Res.string.calendar_previous_month)
                )
            }
            IconButton(onClick = onNextMonth, enabled = canGoForward) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(Res.string.calendar_next_month)
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeaderRow(modifier: Modifier = Modifier) {
    Row(
        // Decorative: every cell below already announces its own date and duration.
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Monday-first, per the design's M T W T F S S header.
        DayOfWeekNames.ENGLISH_ABBREVIATED.names.forEach { name ->
            Text(
                text = name.take(1),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MonthFooter(
    projectColor: Color,
    modifier: Modifier = Modifier
) {
    // Only the key for the tints above. The month's busiest day and its total are statistics
    // rather than parts of the calendar control, so they live in their own cards on the screen.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IntensityLegend(projectColor = projectColor)
    }
}

@Composable
private fun IntensityLegend(projectColor: Color, modifier: Modifier = Modifier) {
    Row(
        // A visual key for the tints above; nothing for a screen reader to announce.
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.calendar_intensity_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        LegendSwatch(MaterialTheme.colorScheme.surfaceVariant)
        LegendSwatch(projectColor.copy(alpha = MIN_INTENSITY))
        LegendSwatch(projectColor.copy(alpha = 0.40f))
        LegendSwatch(projectColor)
        Text(
            text = stringResource(Res.string.calendar_intensity_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun LegendSwatch(color: Color) {
    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val PreviewProjectColor = Color(0xFFF39B19)

@Composable
private fun CardPreviewContainer(content: @Composable () -> Unit) {
    TrackyTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun PreviewCard(month: CalendarMonthUi, projectColor: Color = PreviewProjectColor) {
    CalendarMonthCard(
        month = month,
        selectedDate = LocalDate(2026, 9, 8),
        selectedDateLabel = "Sep 8, 2026",
        projectColor = projectColor,
        onDateSelected = {},
        onPreviousMonth = {},
        onNextMonth = {}
    )
}

@PreviewLightDark
@Composable
private fun CalendarMonthCardDefaultPreview() {
    CardPreviewContainer { PreviewCard(previewCalendarMonth()) }
}

/** Nothing tracked: the header says so, and the footer has no busiest day to name. */
@PreviewLightDark
@Composable
private fun CalendarMonthCardEmptyPreview() {
    CardPreviewContainer {
        PreviewCard(
            previewCalendarMonth(tracked = emptyMap())
                .copy(monthTotalLabel = null, busiestDayLabel = null)
        )
    }
}

/** The oldest month: there is nothing before it, so the back chevron is disabled. */
@Preview(name = "At the start of the range")
@Composable
private fun CalendarMonthCardAtRangeStartPreview() {
    CardPreviewContainer {
        CalendarMonthCard(
            month = previewCalendarMonth(),
            selectedDate = LocalDate(2026, 9, 8),
            selectedDateLabel = "Sep 8, 2026",
            projectColor = PreviewProjectColor,
            onDateSelected = {},
            onPreviousMonth = {},
            onNextMonth = {},
            canGoBack = false
        )
    }
}

/** Paged away from today: the jump-to-today action has nowhere to go, so it disables. */
@Preview(name = "Today not on this page")
@Composable
private fun CalendarMonthCardTodayElsewherePreview() {
    CardPreviewContainer {
        PreviewCard(previewCalendarMonth(today = LocalDate(2026, 11, 3)))
    }
}

@Preview(name = "Compact", widthDp = 320)
@Composable
private fun CalendarMonthCardCompactPreview() {
    CardPreviewContainer { PreviewCard(previewCalendarMonth()) }
}

@Preview(name = "Expanded width", widthDp = 840)
@Composable
private fun CalendarMonthCardExpandedWidthPreview() {
    CardPreviewContainer { PreviewCard(previewCalendarMonth()) }
}

/** The footer packs a label and six swatches onto one row, so large text is the tight case. */
@Preview(name = "Font scale 2x", fontScale = 2f)
@Composable
private fun CalendarMonthCardFontScalePreview() {
    CardPreviewContainer { PreviewCard(previewCalendarMonth()) }
}
