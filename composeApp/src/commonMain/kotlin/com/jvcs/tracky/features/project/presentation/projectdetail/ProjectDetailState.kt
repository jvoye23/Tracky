package com.jvcs.tracky.features.project.presentation.projectdetail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.jvcs.tracky.features.project.presentation.models.PerDayStripUi
import com.jvcs.tracky.features.project.presentation.models.ProjectUi

@Stable
data class ProjectDetailState(
    val project: ProjectUi? = null,
    val isEditMode: Boolean = false,
    val titleText: String? = null,
    val descriptionText: String? = null,
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
    val perDayStrip: PerDayStripUi? = null,
    /**
     * True while the timer running in this project belongs to another device.
     *
     * Kept on the state rather than derived in the composable because the hero card has no task to
     * read it from: the project's own running state is the union of its tasks'.
     */
    val isRunningTimerForeign: Boolean = false,
    /** True while that foreign timer's figure is frozen at the last one the server confirmed. */
    val isRunningTimerStale: Boolean = false,
)
