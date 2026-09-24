package com.jvcs.tracky.features.project.presentation.dailyoverview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.designsystem.components.InfoCard
import com.jvcs.tracky.designsystem.theme.SampleProjectColors
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.theme.defaultProjectColor
import com.jvcs.tracky.designsystem.util.ObserveAsEvents
import com.jvcs.tracky.features.project.presentation.dailyoverview.components.CalendarMonthCard
import com.jvcs.tracky.features.project.presentation.dailyoverview.components.DayEmptyState
import com.jvcs.tracky.features.project.presentation.dailyoverview.components.DayHeaderRow
import com.jvcs.tracky.features.project.presentation.dailyoverview.components.DayIntervalCard
import com.jvcs.tracky.features.project.presentation.dailyoverview.components.previewCalendarMonth
import com.jvcs.tracky.features.project.presentation.models.DayDetailUi
import com.jvcs.tracky.features.project.presentation.models.DayIntervalUi
import com.jvcs.tracky.features.project.presentation.projectdetail.components.ProjectSubDetailTopAppBar
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.calendar_busiest_day_label
import tracky.composeapp.generated.resources.calendar_month_total_label
import tracky.composeapp.generated.resources.calendar_no_value
import tracky.composeapp.generated.resources.daily_overview_title

@Composable
fun DailyOverviewScreenRoot(navigateBack: () -> Unit, viewModel: DailyOverviewViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DailyOverviewEvent.Error -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.error.asStringAsync(),
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }

    DailyOverviewScreen(
        state = state,
        onAction = { action ->
            when (action) {
                DailyOverviewAction.OnBackClick -> navigateBack()
                else -> Unit
            }
            viewModel.onAction(action)
        },
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyOverviewScreen(
    state: DailyOverviewState,
    onAction: (DailyOverviewAction) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ProjectSubDetailTopAppBar(
                title = stringResource(Res.string.daily_overview_title).uppercase(),
                onNavigateBack = { onAction(DailyOverviewAction.OnBackClick) },
                projectColor = state.projectColor ?: MaterialTheme.colorScheme.primary,
                showEditAction = false,
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // The project colour is user-picked and may be unset; the calendar always needs one.
        val projectColor = state.projectColor ?: defaultProjectColor

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "calendar") {
                CalendarMonthCard(
                    months = state.months,
                    currentIndex = state.visibleMonthIndex,
                    selectedDate = state.selectedDate,
                    selectedDateLabel = state.dayDetail?.headlineLabel,
                    projectColor = projectColor,
                    onDateSelected = { onAction(DailyOverviewAction.OnDateSelected(it)) },
                    onMonthChange = { onAction(DailyOverviewAction.OnMonthChanged(it)) },
                )
            }

            // Follows the visible month rather than the selected day, so paging updates it.
            state.months.getOrNull(state.visibleMonthIndex)?.let { month ->
                item(key = "month-summary") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoCard(
                            Modifier.weight(1f),
                            Icons.Outlined.LocalFireDepartment,
                            stringResource(Res.string.calendar_busiest_day_label),
                            month.busiestDayLabel ?: stringResource(Res.string.calendar_no_value),
                        )
                        InfoCard(
                            Modifier.weight(1f),
                            Icons.Outlined.Schedule,
                            stringResource(Res.string.calendar_month_total_label),
                            month.monthTotalLabel ?: stringResource(Res.string.calendar_no_value),
                        )
                    }
                }
            }

            state.dayDetail?.let { day ->
                item(key = "day-header") {
                    DayHeaderRow(day = day, modifier = Modifier.padding(top = 8.dp))
                }

                if (day.isEmpty) {
                    item(key = "day-empty") { DayEmptyState() }
                } else {
                    items(day.intervals, key = { it.intervalId }) { interval ->
                        DayIntervalCard(
                            interval = interval,
                            projectColor = projectColor,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Suppress("MagicNumber") // preview fixture: sample data is meant to be literal
private fun previewInterval(
    index: String,
    task: String,
    subTask: String?,
    range: String,
    duration: String,
) = DayIntervalUi("id-$index", index, task, subTask, range, duration, SampleProjectColors.Slate)

@Suppress("MagicNumber") // preview fixture: sample data is meant to be literal
private fun previewState(dayDetail: DayDetailUi) =
    DailyOverviewState(
        projectTitle = "Tracky",
        projectColor = null,
        selectedDate = LocalDate(2026, 9, 8),
        months = listOf(previewCalendarMonth()),
        visibleMonthIndex = 0,
        dayDetail = dayDetail,
        isLoading = false,
    )

private fun previewDay() =
    DayDetailUi(
        dateLabel = "Tue, Sep 08",
        headlineLabel = "Sep 8, 2026",
        totalDuration = "03:26:58",
        intervals =
            listOf(
                previewInterval("01", "Design review", "Calendar spec", "09:30 – 10:12", "00:42:11"),
                previewInterval("02", "Onboarding copy", null, "10:20 – 10:58", "00:38:22"),
                previewInterval("03", "Auth endpoints", "Token refresh", "13:15 – 14:47", "01:32:07"),
                previewInterval("04", "Testing Tasks", null, "15:30 – 16:04", "00:34:18"),
            ),
        taskCount = 4,
    )

@Suppress("ScreenStateOnlyInScreenComposable") // preview wrapper: hands the whole state to the Screen
@Composable
private fun ScreenPreview(state: DailyOverviewState) {
    TrackyTheme {
        DailyOverviewScreen(state = state, onAction = {}, snackbarHostState = SnackbarHostState())
    }
}

@PreviewLightDark
@Composable
private fun DailyOverviewScreenPreview() = ScreenPreview(previewState(previewDay()))

/** A day with nothing on it keeps its heading and the calendar above it. */
@PreviewLightDark
@Composable
private fun DailyOverviewScreenEmptyDayPreview() =
    ScreenPreview(previewState(DayDetailUi("Wed, Sep 09", "Sep 9, 2026", "00:00:00", emptyList(), 0)))

@Preview(name = "Loading")
@Composable
private fun DailyOverviewScreenLoadingPreview() = ScreenPreview(DailyOverviewState())
