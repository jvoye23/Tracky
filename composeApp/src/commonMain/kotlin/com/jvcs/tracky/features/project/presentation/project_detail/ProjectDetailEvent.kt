package com.jvcs.tracky.features.project.presentation.project_detail

import com.jvcs.tracky.design_system.util.UiText

sealed interface ProjectDetailEvent {
    data class NewProjectSessionSaved(val projectSessionTitle: String): ProjectDetailEvent
    data class Error(val error: UiText): ProjectDetailEvent
}