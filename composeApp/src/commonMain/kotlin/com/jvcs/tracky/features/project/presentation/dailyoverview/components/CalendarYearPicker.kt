package com.jvcs.tracky.features.project.presentation.dailyoverview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.designsystem.theme.SampleProjectColors
import com.jvcs.tracky.designsystem.theme.TrackyTheme

/** Three across, the way Material 3's own year picker lays its grid out. */
private const val YEARS_PER_ROW = 3

/**
 * The year grid that opens when the month title is tapped, mirroring Material 3's `YearPicker`.
 *
 * [years] is the range the pager can actually show, so a year picked here always has a page to
 * land on. The grid scrolls the selected year into view on open, which matters for a project
 * that has been running long enough to fill more than one row.
 */
@Composable
fun CalendarYearPicker(
    years: List<Int>,
    selectedYear: Int,
    projectColor: Color,
    onYearSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(years, selectedYear) {
        val index = years.indexOf(selectedYear)
        if (index >= 0) gridState.scrollToItem(index / YEARS_PER_ROW)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(YEARS_PER_ROW),
        state = gridState,
        // Bounded so a long-running project cannot push the grid past the card it sits in.
        modifier = modifier.heightIn(max = 180.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(years, key = { it }) { year ->
            YearCell(
                year = year,
                isSelected = year == selectedYear,
                projectColor = projectColor,
                onClick = { onYearSelect(year) },
            )
        }
    }
}

@Composable
private fun YearCell(
    year: Int,
    isSelected: Boolean,
    projectColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) projectColor else Color.Transparent)
                .then(
                    if (isSelected) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    },
                ).clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = year.toString(),
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                ),
            // The selected cell is filled with the project colour, so its label takes the same
            // black-or-white treatment the day cells use rather than a theme colour.
            color = if (isSelected) onProjectColor(projectColor) else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val PreviewProjectColor = SampleProjectColors.Orange

@Composable
private fun YearPickerPreviewContainer(content: @Composable () -> Unit) {
    TrackyTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@PreviewLightDark
@Composable
private fun CalendarYearPickerPreview() {
    YearPickerPreviewContainer {
        CalendarYearPicker(
            years = (2024..2026).toList(),
            selectedYear = 2026,
            projectColor = PreviewProjectColor,
            onYearSelect = {},
        )
    }
}

/** A project with one year of history still fills a sensible grid rather than one lonely cell. */
@Preview(name = "Single year")
@Composable
private fun CalendarYearPickerSingleYearPreview() {
    YearPickerPreviewContainer {
        CalendarYearPicker(
            years = listOf(2026),
            selectedYear = 2026,
            projectColor = PreviewProjectColor,
            onYearSelect = {},
        )
    }
}

/** Long history: the grid scrolls, and the selected year is scrolled into view on open. */
@Preview(name = "Many years")
@Composable
private fun CalendarYearPickerManyYearsPreview() {
    YearPickerPreviewContainer {
        CalendarYearPicker(
            years = (2015..2026).toList(),
            selectedYear = 2025,
            projectColor = PreviewProjectColor,
            onYearSelect = {},
        )
    }
}

@Preview(name = "Font scale 2x", fontScale = 2f)
@Composable
private fun CalendarYearPickerFontScalePreview() {
    YearPickerPreviewContainer {
        CalendarYearPicker(
            years = (2024..2026).toList(),
            selectedYear = 2026,
            projectColor = PreviewProjectColor,
            onYearSelect = {},
        )
    }
}
