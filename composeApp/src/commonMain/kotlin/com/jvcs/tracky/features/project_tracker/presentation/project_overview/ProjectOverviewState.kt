package com.jvcs.tracky.features.project_tracker.presentation.project_overview

import androidx.compose.foundation.text.input.TextFieldState
import com.jvcs.tracky.core.presentation.model.ProjectUi

data class ProjectOverviewState(
    val timer: Long = 0L,
    val label: String = "",
    val isAddNewProjectBottomSheetVisible: Boolean = false,
    val addProjectTextFieldState: TextFieldState = TextFieldState(),
    val projects: List<ProjectUi>? = null
)