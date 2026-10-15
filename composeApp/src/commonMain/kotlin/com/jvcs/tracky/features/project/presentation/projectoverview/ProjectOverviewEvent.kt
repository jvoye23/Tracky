package com.jvcs.tracky.features.project.presentation.projectoverview

import com.jvcs.tracky.designsystem.util.UiText

sealed interface ProjectOverviewEvent {

    data class NewProjectSaved(val projectId: String) : ProjectOverviewEvent

    data class Error(val error: UiText) : ProjectOverviewEvent

    /** The server refused to sync because the account is not Pro; offered with a way to upgrade. */
    data object ProRequired : ProjectOverviewEvent

    /** A free user reached for a Pro feature: open the paywall. */
    data object NavigateToPaywall : ProjectOverviewEvent

    data object ArchiveError : ProjectOverviewEvent

    data object PinError : ProjectOverviewEvent

    data class AddToTrashError(val error: UiText) : ProjectOverviewEvent

    data class ReorderError(val error: UiText) : ProjectOverviewEvent

    data class OnLogoutError(val error: UiText) : ProjectOverviewEvent

    data object OnLogoutSuccess : ProjectOverviewEvent
}
