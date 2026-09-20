package com.jvcs.tracky.features.project.presentation.project_detail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.graphics.Color
import com.jvcs.tracky.features.project.presentation.models.PerDayStripUi
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import kotlin.time.Duration

data class ProjectDetailState(
    val project: ProjectUi? = null,
    val isEditMode: Boolean = false,
    val titleText: String? = null,
    val descriptionText: String? = null,
    val formattedTimerString: String = "00:00:00",
    val timerDuration: Duration? = null,
    //val isTimerRunning: Boolean = false,
    val isFabExtended: Boolean = true,
    val errorMessage: String? = null,
    val isAddNewProjectTaskBottomSheetVisible: Boolean = false,
    val addProjectTaskTextFieldState: TextFieldState = TextFieldState(),
    // Ids of tasks whose subtask list is collapsed. Held here rather than in the card because the
    // cards are LazyColumn items — remember{} inside one is dropped when it scrolls out of view.
    val collapsedTaskIds: Set<String> = emptySet(),
    // Raised when the user tries to uncheck a task that owns subtasks; see onTaskCheckedChange.
    val isUncheckTaskBlockedDialogVisible: Boolean = false,
    val isColorPickerVisible: Boolean = false,
    val projectColor: Color? = null,
    val selectedColorHex: String = "#00FFFF",
    val useLightTextColor: Boolean = false,
    // Null until the project loads, and stays null for a project with no banked time — the
    // strip is omitted rather than drawn empty. Survives the project-row combine because
    // withProjectRow only rewrites the fields the row owns.
    val perDayStrip: PerDayStripUi? = null
)
