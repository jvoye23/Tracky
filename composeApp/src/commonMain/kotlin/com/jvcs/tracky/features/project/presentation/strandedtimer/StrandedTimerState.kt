package com.jvcs.tracky.features.project.presentation.strandedtimer

import androidx.compose.foundation.text.input.TextFieldState
import com.jvcs.tracky.features.project.domain.timer.StrandedTimer

/**
 * The review queue, one item at a time.
 *
 * One at a time rather than a combined prompt: several timers can be parked at once — different
 * tasks, different launches — and collapsing them would hide all but the first.
 */
data class StrandedTimerState(
    val pending: List<StrandedTimer> = emptyList(),
    val isEditingDuration: Boolean = false,
    /** "HH:mm", the editable form of the offered duration. */
    val editDurationState: TextFieldState = TextFieldState(),
    /** Set while a resolution is in flight, so a double tap cannot resolve the same item twice. */
    val isResolving: Boolean = false,
) {
    val current: StrandedTimer? get() = pending.firstOrNull()
}
