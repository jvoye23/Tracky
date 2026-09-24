package com.jvcs.tracky.features.project.presentation.stranded_timer

import com.jvcs.tracky.designsystem.util.UiText

sealed interface StrandedTimerEvent {
    /** The item stays in the queue — a failed resolution must not look like a resolved one. */
    data class Error(val error: UiText) : StrandedTimerEvent
}
