package com.jvcs.tracky.features.project_tracker.presentation.project_detail

import androidx.compose.foundation.text.input.TextFieldState
import com.jvcs.tracky.core.presentation.model.ProjectSessionUi
import com.jvcs.tracky.core.presentation.model.ProjectUi
import kotlin.time.Duration

data class ProjectDetailState(
    val project: ProjectUi? = null,
    val isEditMode: Boolean = false,
    val titleText: String? = null,
    val descriptionText: String? = null,
    val formattedTimerString: String = "00:00:00:00",
    val timerDuration: Duration? = null,
    //val isTimerRunning: Boolean = false,
    val isFabExtended: Boolean = true,
    val errorMessage: String? = null,
    val isAddNewProjectSessionBottomSheetVisible: Boolean = false,
    val addProjectSessionTextFieldState: TextFieldState = TextFieldState(),
)
