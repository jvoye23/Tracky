package com.jvcs.tracky.features.project_tracker.presentation.project_detail

import com.jvcs.tracky.design_system.util.UiText
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewEvent
import com.jvcs.tracky.navigation.Route

sealed interface ProjectDetailEvent {
    data class NewProjectSessionSaved(val projectSessionTitle: String): ProjectDetailEvent
    data class Error(val error: UiText): ProjectDetailEvent
}