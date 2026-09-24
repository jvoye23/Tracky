package com.jvcs.tracky.features.project.presentation.strandedtimer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.timer.StrandedTimer
import com.jvcs.tracky.features.project.domain.timer.StrandedTimerRepository
import com.jvcs.tracky.features.project.presentation.util.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private const val MAX_MINUTES = 59

class StrandedTimerViewModel(private val strandedTimerRepository: StrandedTimerRepository) : ViewModel() {

    private val eventChannel = Channel<StrandedTimerEvent>()
    val events = eventChannel.receiveAsFlow()

    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(StrandedTimerState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    observeStrandedTimers()
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = StrandedTimerState(),
            )

    private fun observeStrandedTimers() {
        strandedTimerRepository
            .observeStrandedTimers()
            .onEach { timers ->
                _state.update { current ->
                    // The edit field belongs to whichever item is on top; a queue that shifted
                    // under it must not carry a half-typed duration onto the next one.
                    val sameItem = current.current?.id == timers.firstOrNull()?.id
                    if (sameItem) {
                        current.copy(pending = timers)
                    } else {
                        current.copy(
                            pending = timers,
                            isEditingDuration = false,
                            editDurationState = TextFieldState(),
                        )
                    }
                }
            }.launchIn(viewModelScope)
    }

    fun onAction(action: StrandedTimerAction) {
        when (action) {
            StrandedTimerAction.OnKeep -> {
                resolve { strandedTimerRepository.keep(it) }
            }

            StrandedTimerAction.OnDiscard -> {
                resolve { strandedTimerRepository.discard(it) }
            }

            StrandedTimerAction.OnBeginEditDuration -> {
                beginEdit()
            }

            StrandedTimerAction.OnCancelEditDuration -> {
                _state.update {
                    it.copy(isEditingDuration = false)
                }
            }

            StrandedTimerAction.OnConfirmEditedDuration -> {
                val typed =
                    parseHoursMinutes(
                        _state.value.editDurationState.text
                            .toString(),
                    )
                        ?: return
                resolve { strandedTimerRepository.keepWithDuration(it, typed) }
            }
        }
    }

    /** Seeds the field with the offered duration, so editing starts from it rather than blank. */
    private fun beginEdit() {
        val offered = _state.value.current?.proposedDuration ?: return
        _state.update {
            it.copy(
                isEditingDuration = true,
                editDurationState = TextFieldState(formatHoursMinutes(offered)),
            )
        }
    }

    private fun resolve(block: suspend (StrandedTimer) -> EmptyResult<DataError>) {
        val timer = _state.value.current ?: return
        if (_state.value.isResolving) return

        viewModelScope.launch {
            _state.update { it.copy(isResolving = true) }
            val result = block(timer)
            _state.update { it.copy(isResolving = false) }
            // On success the repository's flow drops the item and the dialog advances on its own.
            // On failure it stays put: a resolution that did not happen must not look like one.
            if (result is Result.Error) {
                eventChannel.send(StrandedTimerEvent.Error(result.error.toUiText()))
            }
        }
    }

    companion object {
        /** "3:07" — hours and minutes, the only granularity worth typing for a multi-day gap. */
        internal fun formatHoursMinutes(duration: Duration): String =
            duration.toComponents { hours, minutes, _, _ -> "$hours:${minutes.toString().padStart(2, '0')}" }

        /** Accepts "3:07" and a bare "3". Returns null for anything else, which leaves the field alone. */
        internal fun parseHoursMinutes(text: String): Duration? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null

            val parts = trimmed.split(":")
            if (parts.size > 2) return null

            val hours = parts[0].toLongOrNull() ?: return null
            val minutes = if (parts.size == 2) parts[1].toLongOrNull() ?: return null else 0L
            if (hours < 0 || minutes < 0 || minutes > MAX_MINUTES) return null
            return hours.hours + minutes.minutes
        }
    }
}
