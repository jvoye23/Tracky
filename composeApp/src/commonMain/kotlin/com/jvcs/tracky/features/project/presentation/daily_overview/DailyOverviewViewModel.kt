package com.jvcs.tracky.features.project.presentation.daily_overview

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.platformIoDispatcher
import com.jvcs.tracky.designsystem.util.UiText
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.presentation.mappers.toCalendarMonthsUi
import com.jvcs.tracky.features.project.presentation.mappers.toDayDetailUi
import com.jvcs.tracky.features.project.presentation.models.CalendarMonthUi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.error_unknown
import kotlin.time.ExperimentalTime

/**
 * The daily overview reads the project's whole task tree once and derives everything from it in
 * memory.
 *
 * The tree only arrives through the suspend one-shot `getProjectWithTasksByProjectId` —
 * `observeProjectById` streams the project row alone, with no tasks and no intervals — so
 * `ProjectDetailViewModel` reads it the same way. Picking a different day or month therefore
 * re-runs the pure mappers against the tree already in hand and never touches the database.
 *
 * The cost is that the screen does not live-update if a sync pull lands while it is open. That is
 * acceptable here: the screen is read-only, and the counted set drops open intervals anyway, so a
 * running timer would have nothing to stream.
 */
@OptIn(ExperimentalTime::class)
class DailyOverviewViewModel(
    private val projectId: String,
    private val preselectedDateEpochDay: Long,
    private val projectRepository: ProjectRepository,
    private val timeProvider: TimeProvider,
    private val savedStateHandle: SavedStateHandle,
    // Injectable so tests can drive the initial load on their own scheduler; production keeps IO.
    private val ioDispatcher: CoroutineDispatcher = platformIoDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(DailyOverviewState())
    private val eventChannel = Channel<DailyOverviewEvent>()
    val events = eventChannel.receiveAsFlow()
    private var hasLoadedInitialData = false

    /** The tree every mapper reads. Held rather than re-fetched; see the class comment. */
    private var project: Project? = null

    private val timeZone = TimeZone.currentSystemDefault()

    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    loadProject()
                    hasLoadedInitialData = true
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    fun onAction(action: DailyOverviewAction) {
        when (action) {
            // Handled in the UI.
            DailyOverviewAction.OnBackClick -> Unit

            is DailyOverviewAction.OnDateSelected -> selectDate(action.date)

            is DailyOverviewAction.OnMonthChanged -> changeMonth(action.index)
        }
    }

    private fun loadProject() =
        viewModelScope.launch {
            val loaded =
                withContext(ioDispatcher) {
                    projectRepository.getProjectWithTasksByProjectId(projectId)
                }

            if (loaded == null) {
                _state.update { it.copy(isLoading = false) }
                eventChannel.send(DailyOverviewEvent.Error(UiText.Resource(Res.string.error_unknown)))
                return@launch
            }

            project = loaded
            val today = timeProvider.nowInstant.toLocalDateTime(timeZone).date
            val months = loaded.toCalendarMonthsUi(today = today, timeZone = timeZone)
            // A date restored after process death outranks the one navigation supplied, so coming
            // back to the screen returns to the day the user was actually looking at.
            val selected = restoredDate() ?: preselectedDate() ?: today

            _state.update {
                it.copy(
                    projectTitle = loaded.title,
                    projectColor = loaded.colorArgb?.let(::Color),
                    selectedDate = selected,
                    months = months,
                    visibleMonthIndex = months.indexOfMonthOf(selected).coerceAtLeast(0),
                    dayDetail = loaded.toDayDetailUi(selected, timeZone),
                    isLoading = false,
                )
            }
        }

    private fun selectDate(date: LocalDate) {
        val loaded = project ?: return
        savedStateHandle[KEY_SELECTED_DATE] = date.toEpochDays()
        _state.update {
            it.copy(
                selectedDate = date,
                // Selecting a day inside the visible month must not page the calendar; only a day
                // reached some other way moves it.
                visibleMonthIndex =
                    it.months.indexOfMonthOf(date).takeIf { i -> i >= 0 }
                        ?: it.visibleMonthIndex,
                dayDetail = loaded.toDayDetailUi(date, timeZone),
            )
        }
    }

    /** Paging alone never changes the selected day — the day list keeps showing what was picked. */
    private fun changeMonth(index: Int) =
        _state.update {
            it.copy(visibleMonthIndex = index.coerceIn(it.months.indices))
        }

    private fun restoredDate(): LocalDate? =
        savedStateHandle.get<Long>(KEY_SELECTED_DATE)?.let(LocalDate::fromEpochDays)

    private fun preselectedDate(): LocalDate? =
        preselectedDateEpochDay.takeIf { it != NO_PRESELECTED_DATE }?.let(LocalDate::fromEpochDays)

    /** The page showing [date]'s month, or -1 when the range does not reach it. */
    private fun List<CalendarMonthUi>.indexOfMonthOf(date: LocalDate): Int =
        indexOfFirst { it.yearMonth.year == date.year && it.yearMonth.month == date.month }

    internal companion object {
        const val KEY_SELECTED_DATE = "dailyOverview.selectedDate"

        /** Epoch day 0 is a real date, so "none" needs a value outside the range. */
        const val NO_PRESELECTED_DATE = -1L
    }
}
