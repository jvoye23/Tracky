package com.jvcs.tracky.features.project_tracker.presentation.project_overview

import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.design_system.util.UiText

interface ProjectOverviewEvent {

    data class NewProjectSaved(val projectId: String): ProjectOverviewEvent
    data class Error(val error: UiText): ProjectOverviewEvent
    data object ArchiveError: ProjectOverviewEvent
    data object PinError: ProjectOverviewEvent
}