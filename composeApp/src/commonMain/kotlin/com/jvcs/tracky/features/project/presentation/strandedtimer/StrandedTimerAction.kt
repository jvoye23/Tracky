package com.jvcs.tracky.features.project.presentation.strandedtimer

sealed interface StrandedTimerAction {
    /** Banks the whole elapsed span. */
    data object OnKeep : StrandedTimerAction

    data object OnBeginEditDuration : StrandedTimerAction

    data object OnCancelEditDuration : StrandedTimerAction

    /** Banks whatever is in the field instead. */
    data object OnConfirmEditedDuration : StrandedTimerAction

    data object OnDiscard : StrandedTimerAction
}
