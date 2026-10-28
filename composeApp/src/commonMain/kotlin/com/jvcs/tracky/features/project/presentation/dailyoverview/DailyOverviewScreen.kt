package com.jvcs.tracky.features.project.presentation.dailyoverview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.designsystem.components.InfoCard
import com.jvcs.tracky.designsystem.components.layouts.TrackyAdaptiveDetailLayout
import com.jvcs.tracky.designsystem.components.layouts.TwoPaneTopGap
import com.jvcs.tracky.designsystem.components.layouts.detailHeaderTopInset
import com.jvcs.tracky.designsystem.components.layouts.rememberDetailTopBarScrollBehavior
import com.jvcs.tracky.designsystem.theme.SampleProjectColors
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.theme.defaultProjectColor
import com.jvcs.tracky.designsystem.util.DeviceConfiguration
import com.jvcs.tracky.designsystem.util.ObserveAsEvents
import com.jvcs.tracky.designsystem.util.PreviewDevices
import com.jvcs.tracky.designsystem.util.currentDeviceConfiguration
import com.jvcs.tracky.features.project.presentation.dailyoverview.components.CalendarMonthCard
import com.jvcs.tracky.features.project.presentation.dailyoverview.components.DAYS_PER_WEEK
import com.jvcs.tracky.features.project.presentation.dailyoverview.components.DayEmptyState
import com.jvcs.tracky.features.project.presentation.dailyoverview.components.DayHeaderRow
import com.jvcs.tracky.features.project.presentation.dailyoverview.components.DayIntervalCard
import com.jvcs.tracky.features.project.presentation.dailyoverview.components.previewCalendarMonth
import com.jvcs.tracky.features.project.presentation.models.CalendarMonthUi
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
import kotlin.math.absoluteValue

private val ScreenPadding = 16.dp
private val ItemSpacing = 10.dp

/** Roughly a large phone's width: the calendar keeps its phone size on wider windows. */
private val CalendarMaxWidth = 480.dp

/**
 * The narrowest a two-pane calendar gets to fit its column. A column too short even for that (a
 * phone on its side) keeps the calendar at full size and scrolls instead.
 */
private val CalendarMinWidth = 280.dp

/** Keeps the bottom of the two-pane calendar column clear of the screen edge. */
private val SummaryBottomGap = 16.dp

/** Every calendar page is six weeks, whatever the month. */
private const val CALENDAR_WEEKS = 6

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
    val listState = rememberLazyListState()
    // The two-pane calendar column; scrolling it collapses the top bar just like the list does.
    val summaryScrollState = rememberScrollState()
    // One instance, shared by the app bar and the layout's nested-scroll connection below.
    val scrollBehavior = rememberDetailTopBarScrollBehavior(listState, summaryScrollState)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ProjectSubDetailTopAppBar(
                title = stringResource(Res.string.daily_overview_title).uppercase(),
                onNavigateBack = { onAction(DailyOverviewAction.OnBackClick) },
                projectColor = state.projectColor ?: MaterialTheme.colorScheme.primary,
                showEditAction = false,
                // Opaque, because the content scrolls underneath it.
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                scrollBehavior = scrollBehavior,
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

        // A phone on its side is too short for the calendar and the month's cards together, so
        // the cards move to the top of the list beside it.
        val monthSummaryInList = currentDeviceConfiguration() == DeviceConfiguration.MOBILE_LANDSCAPE
        val visibleMonth = state.months.getOrNull(state.visibleMonthIndex)

        // The content runs behind the collapsing bar, so it pads for the expanded bar itself: the
        // Scaffold's top padding shrinks as the bar collapses and would drag the content along.
        val headerTopInset = detailHeaderTopInset()

        BoxWithConstraints {
            // What the left column of the two-pane layout shows without scrolling, below the
            // expanded bar.
            val twoPaneSummaryHeight =
                maxHeight - headerTopInset - TwoPaneTopGap - innerPadding.calculateBottomPadding() - SummaryBottomGap

            TrackyAdaptiveDetailLayout(
                listState = listState,
                contentPadding = innerPadding,
                listTopPadding = headerTopInset,
                listItemSpacing = ItemSpacing,
                summaryScrollState = summaryScrollState,
                topBarScrollConnection = scrollBehavior.nestedScrollConnection,
                summary = { isTwoPane ->
                    DailyOverviewSummary(
                        months = state.months,
                        visibleMonthIndex = state.visibleMonthIndex,
                        selectedDate = state.selectedDate,
                        selectedDateLabel = state.dayDetail?.headlineLabel,
                        projectColor = projectColor,
                        showMonthSummary = !monthSummaryInList,
                        maxHeight = if (isTwoPane) twoPaneSummaryHeight else null,
                        onAction = onAction,
                        modifier = Modifier.padding(top = if (isTwoPane) 0.dp else headerTopInset + 12.dp),
                    )
                },
            ) { isTwoPane ->
                if (monthSummaryInList && visibleMonth != null) {
                    item(key = "month-summary") {
                        MonthSummaryCards(
                            month = visibleMonth,
                            modifier = Modifier.padding(horizontal = ScreenPadding),
                        )
                    }
                }

                state.dayDetail?.let { day ->
                    item(key = "day-header") {
                        DayHeaderRow(
                            day = day,
                            modifier =
                                Modifier
                                    .padding(horizontal = ScreenPadding)
                                    .padding(top = if (isTwoPane && !monthSummaryInList) 0.dp else 8.dp),
                        )
                    }

                    if (day.isEmpty) {
                        item(key = "day-empty") {
                            DayEmptyState(modifier = Modifier.padding(horizontal = ScreenPadding))
                        }
                    } else {
                        items(day.intervals, key = { it.intervalId }) { interval ->
                            DayIntervalCard(
                                interval = interval,
                                projectColor = projectColor,
                                modifier = Modifier.padding(horizontal = ScreenPadding),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The calendar and, unless [showMonthSummary] is off, the month's summary cards. Capped at a
 * phone's width and centred, because the calendar's cells are square: stretched across a tablet it
 * would grow taller than the screen.
 *
 * @param maxHeight in two-pane, the height of the column; the summary narrows until it fits, and
 * keeps its full width and scrolls when fitting would take it below [CalendarMinWidth]. Null in
 * portrait, where the page scrolls.
 */
@Composable
private fun DailyOverviewSummary(
    months: List<CalendarMonthUi>,
    visibleMonthIndex: Int,
    selectedDate: LocalDate?,
    selectedDateLabel: String?,
    projectColor: Color,
    showMonthSummary: Boolean,
    maxHeight: Dp?,
    onAction: (DailyOverviewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Starts at the cap and is corrected after the first measurement; keyed on the height so a
    // rotation or a window resize fits again from scratch.
    var fittedMaxWidth by remember(maxHeight) { mutableStateOf(CalendarMaxWidth) }
    val density = LocalDensity.current

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = fittedMaxWidth)
                .then(
                    if (maxHeight == null) {
                        Modifier
                    } else {
                        Modifier.onSizeChanged { size ->
                            val fitted =
                                with(density) { fittedWidth(size.width.toDp(), size.height.toDp(), maxHeight) }
                            // The threshold stops sub-pixel rounding from ping-ponging the width.
                            if ((fitted - fittedMaxWidth).value.absoluteValue > 1f) fittedMaxWidth = fitted
                        }
                    },
                ).padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        CalendarMonthCard(
            months = months,
            currentIndex = visibleMonthIndex,
            selectedDate = selectedDate,
            selectedDateLabel = selectedDateLabel,
            projectColor = projectColor,
            onDateSelect = { onAction(DailyOverviewAction.OnDateSelected(it)) },
            onMonthChange = { onAction(DailyOverviewAction.OnMonthChanged(it)) },
        )

        // Follows the visible month rather than the selected day, so paging updates it.
        if (showMonthSummary) {
            months.getOrNull(visibleMonthIndex)?.let { month -> MonthSummaryCards(month = month) }
        }
    }
}

/**
 * The widest the summary can be and still be [maxHeight] tall. The calendar's six rows of seven
 * square cells make its height change by 6/7 of any change in width, and nothing else in it
 * depends on the width, so one measurement is enough. (The year picker breaks that rule while it
 * is open, which only costs a refit once it closes.)
 */
private fun fittedWidth(
    width: Dp,
    height: Dp,
    maxHeight: Dp,
): Dp {
    val fitted = width + (maxHeight - height) * DAYS_PER_WEEK / CALENDAR_WEEKS
    // Shrinking only to scroll anyway would just make the days smaller.
    return if (fitted < CalendarMinWidth) CalendarMaxWidth else fitted.coerceAtMost(CalendarMaxWidth)
}

@Composable
private fun MonthSummaryCards(month: CalendarMonthUi, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(
            icon = Icons.Outlined.LocalFireDepartment,
            label = stringResource(Res.string.calendar_busiest_day_label),
            value = month.busiestDayLabel ?: stringResource(Res.string.calendar_no_value),
            modifier = Modifier.weight(1f),
        )
        InfoCard(
            icon = Icons.Outlined.Schedule,
            label = stringResource(Res.string.calendar_month_total_label),
            value = month.monthTotalLabel ?: stringResource(Res.string.calendar_no_value),
            modifier = Modifier.weight(1f),
        )
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

/** Portrait stacks the list under the calendar; landscape puts it beside it. */
@PreviewDevices
@Composable
private fun DailyOverviewScreenDevicesPreview() = ScreenPreview(previewState(previewDay()))

/** A day with nothing on it keeps its heading and the calendar above it. */
@PreviewLightDark
@Composable
private fun DailyOverviewScreenEmptyDayPreview() =
    ScreenPreview(previewState(DayDetailUi("Wed, Sep 09", "Sep 9, 2026", "00:00:00", emptyList(), 0)))

@Preview(name = "Loading")
@Composable
private fun DailyOverviewScreenLoadingPreview() = ScreenPreview(DailyOverviewState())
