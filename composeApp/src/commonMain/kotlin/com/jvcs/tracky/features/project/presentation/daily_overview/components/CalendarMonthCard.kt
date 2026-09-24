package com.jvcs.tracky.features.project.presentation.daily_overview.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.models.CalendarMonthUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.format.DayOfWeekNames
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.calendar_intensity_less
import tracky.composeapp.generated.resources.calendar_intensity_more
import tracky.composeapp.generated.resources.calendar_jump_to_today
import tracky.composeapp.generated.resources.calendar_next_month
import tracky.composeapp.generated.resources.calendar_previous_month
import tracky.composeapp.generated.resources.calendar_select_date_title
import tracky.composeapp.generated.resources.calendar_select_year

/**
 * The calendar card: a fixed header and footer around a pager of month grids.
 *
 * Only the grid pages, the way Material 3's own date picker works — the title, chevrons and
 * legend stay put while the days slide. [months] is every page the calendar can show, built up
 * front, so a swipe never waits on a recomputation.
 *
 * The pager owns which page is on screen, and the header reads it straight off the pager, so the
 * two can never disagree. [currentIndex] is the command in - the chevrons, a year, or a date
 * picked outside the visible month page the calendar through it - and [onMonthChange] the report
 * out, telling the ViewModel where a swipe landed. The only thing kept locally is whether the year
 * grid is open, which is view state and nothing else.
 */
@Composable
fun CalendarMonthCard(
    months: List<CalendarMonthUi>,
    currentIndex: Int,
    selectedDate: LocalDate?,
    selectedDateLabel: String?,
    projectColor: Color,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (months.isEmpty()) return
    val index = currentIndex.coerceIn(months.indices)

    val pagerState = rememberPagerState(initialPage = index) { months.size }
    var isYearPickerOpen by remember { mutableStateOf(false) }

    // What the pager is actually showing. Reading the hoisted index here instead would make the
    // header wait on a round trip through the ViewModel, and leave it stuck on the wrong month
    // whenever the trip does not happen.
    val displayedIndex = pagerState.currentPage.coerceIn(months.indices)
    val month = months[displayedIndex]

    // Today is already flagged on its cell, so jump-to-today needs no extra input and no round
    // trip through the ViewModel - selecting the date is the whole behaviour, and selectDate
    // pages the calendar there. toCalendarMonthsUi always includes today's month, so this only
    // comes back null for a caller that passed a partial page list.
    val todayDate = months.firstNotNullOfOrNull { m -> m.monthDays.firstOrNull { it.isToday }?.date }

    // Two directions to keep in step: a swipe settles the pager and has to tell the caller, and
    // a caller-driven jump (the chevrons, or a year) has to move the pager.
    //
    // The reporting effect is keyed on the pager, whose identity never changes, so it runs the
    // lambda it was launched with for the life of the card - anything it reads has to come through
    // rememberUpdatedState or it is frozen at the first composition.
    val latestIndex by rememberUpdatedState(index)
    val latestOnMonthChange by rememberUpdatedState(onMonthChange)
    LaunchedEffect(pagerState) {
        // settledPage, not currentPage: reporting mid-gesture would push a new index back down
        // while the finger is still on the screen and let the jump below fight the settle.
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (settled != latestIndex) latestOnMonthChange(settled)
        }
    }
    LaunchedEffect(index) {
        if (pagerState.currentPage != index) pagerState.animateScrollToPage(index)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.03f)),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            MonthHeader(
                month = month,
                selectedDateLabel = selectedDateLabel,
                onJumpToToday = todayDate?.let { date -> { onDateSelected(date) } },
                isYearPickerOpen = isYearPickerOpen,
                onToggleYearPicker = { isYearPickerOpen = !isYearPickerOpen },
                onPreviousMonth = { onMonthChange(displayedIndex - 1) },
                onNextMonth = { onMonthChange(displayedIndex + 1) },
                canGoBack = displayedIndex > 0,
                canGoForward = displayedIndex < months.lastIndex,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (isYearPickerOpen) {
                CalendarYearPicker(
                    years = months.map { it.yearMonth.year }.distinct(),
                    selectedYear = month.yearMonth.year,
                    projectColor = projectColor,
                    onYearSelected = { year ->
                        // Land on the same month of that year when it exists, so picking a year
                        // does not silently move the user to January.
                        val target =
                            months
                                .indexOfFirst {
                                    it.yearMonth.year == year && it.yearMonth.month == month.yearMonth.month
                                }.takeIf { it >= 0 } ?: months.indexOfFirst { it.yearMonth.year == year }
                        if (target >= 0) onMonthChange(target)
                        isYearPickerOpen = false
                    },
                )
            } else {
                WeekdayHeaderRow(modifier = Modifier.padding(horizontal = 12.dp))

                Spacer(modifier = Modifier.height(4.dp))

                HorizontalPager(state = pagerState) { page ->
                    CalendarMonthGrid(
                        month = months[page],
                        selectedDate = selectedDate,
                        projectColor = projectColor,
                        onDateSelected = onDateSelected,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            MonthFooter(
                projectColor = projectColor,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}

/**
 * Follows Material 3's date-picker header: a supporting title, the selected date as the
 * headline, then the month and its chevrons below a divider.
 *
 * @param onJumpToToday null when today is on none of the pages, which disables the action
 * rather than offering a jump that would go nowhere.
 */
@Composable
private fun MonthHeader(
    month: CalendarMonthUi,
    selectedDateLabel: String?,
    onJumpToToday: (() -> Unit)?,
    isYearPickerOpen: Boolean,
    onToggleYearPicker: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.calendar_select_date_title),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = selectedDateLabel.orEmpty(),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onJumpToToday ?: {}, enabled = onJumpToToday != null) {
                Icon(
                    imageVector = Icons.Outlined.Today,
                    contentDescription = stringResource(Res.string.calendar_jump_to_today),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleYearPicker)
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = month.monthLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = if (isYearPickerOpen) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(Res.string.calendar_select_year),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onPreviousMonth, enabled = canGoBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(Res.string.calendar_previous_month),
                )
            }
            IconButton(onClick = onNextMonth, enabled = canGoForward) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(Res.string.calendar_next_month),
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Monday-first, per the design's M T W T F S S header.
        DayOfWeekNames.ENGLISH_ABBREVIATED.names.forEach { name ->
            Text(
                text = name.take(1),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthFooter(projectColor: Color, modifier: Modifier = Modifier) {
    // Only the key for the tints above. The month's busiest day and its total are statistics
    // rather than parts of the calendar control, so they live in their own cards on the screen.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.calendar_intensity_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        LegendSwatch(MaterialTheme.colorScheme.surfaceVariant)
        LegendSwatch(projectColor.copy(alpha = MIN_INTENSITY))
        LegendSwatch(projectColor.copy(alpha = 0.40f))
        LegendSwatch(projectColor)
        Text(
            text = stringResource(Res.string.calendar_intensity_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
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

/** August and September 2026, so the pager and the year picker both have somewhere to go. */
@Suppress("MagicNumber") // preview fixture: sample data is meant to be literal
private fun previewMonths(): List<CalendarMonthUi> =
    listOf(
        previewCalendarMonth().copy(
            yearMonth = YearMonth(2026, 8),
            monthLabel = "August 2026",
            monthTotalLabel = "12:40",
            busiestDayLabel = "Mon 24",
        ),
        previewCalendarMonth(),
    )

@Suppress("MagicNumber") // preview fixture: sample data is meant to be literal
@Composable
private fun PreviewCard(
    months: List<CalendarMonthUi> = previewMonths(),
    currentIndex: Int = 1,
    projectColor: Color = PreviewProjectColor,
) {
    CalendarMonthCard(
        months = months,
        currentIndex = currentIndex,
        selectedDate = LocalDate(2026, 9, 8),
        selectedDateLabel = "Sep 8, 2026",
        projectColor = projectColor,
        onDateSelected = {},
        onMonthChange = {},
    )
}

@PreviewLightDark
@Composable
private fun CalendarMonthCardDefaultPreview() {
    CardPreviewContainer { PreviewCard() }
}

/** Nothing tracked: the header says so, and the footer has no busiest day to name. */
@PreviewLightDark
@Composable
private fun CalendarMonthCardEmptyPreview() {
    CardPreviewContainer {
        PreviewCard(
            months =
                listOf(
                    previewCalendarMonth(tracked = emptyMap())
                        .copy(monthTotalLabel = null, busiestDayLabel = null),
                ),
            currentIndex = 0,
        )
    }
}

/** The oldest month: there is nothing before it, so the back chevron is disabled. */
@Preview(name = "At the start of the range")
@Composable
private fun CalendarMonthCardAtRangeStartPreview() {
    // The oldest page: nothing before it, so the back chevron disables itself.
    CardPreviewContainer { PreviewCard(currentIndex = 0) }
}

@Preview(name = "Compact", widthDp = 320)
@Composable
private fun CalendarMonthCardCompactPreview() {
    CardPreviewContainer { PreviewCard() }
}

@Preview(name = "Expanded width", widthDp = 840)
@Composable
private fun CalendarMonthCardExpandedWidthPreview() {
    CardPreviewContainer { PreviewCard() }
}

/** The footer packs a label and six swatches onto one row, so large text is the tight case. */
@Preview(name = "Font scale 2x", fontScale = 2f)
@Composable
private fun CalendarMonthCardFontScalePreview() {
    CardPreviewContainer { PreviewCard() }
}
