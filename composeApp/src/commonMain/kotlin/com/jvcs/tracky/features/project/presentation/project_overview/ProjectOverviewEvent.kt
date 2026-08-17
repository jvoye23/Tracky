package com.jvcs.tracky.features.project.presentation.project_overview

import com.jvcs.tracky.design_system.util.UiText

sealed interface ProjectOverviewEvent {

    data class NewProjectSaved(val projectId: String): ProjectOverviewEvent
    data class Error(val error: UiText): ProjectOverviewEvent
    data object ArchiveError: ProjectOverviewEvent
    data object PinError: ProjectOverviewEvent
    data class AddToTrashError(val error: UiText): ProjectOverviewEvent
    data class ReorderError(val error: UiText): ProjectOverviewEvent
    data class OnLogoutError(val error: UiText): ProjectOverviewEvent
    data object OnLogoutSuccess: ProjectOverviewEvent
}