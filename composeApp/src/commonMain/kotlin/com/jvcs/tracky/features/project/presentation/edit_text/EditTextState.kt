package com.jvcs.tracky.features.project.presentation.edit_text

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.graphics.Color
import com.jvcs.tracky.features.project.presentation.models.ProjectUi

data class EditTextState(
    val project: ProjectUi? = null,
    val titleState: TextFieldState = TextFieldState(),
    val descriptionState: TextFieldState = TextFieldState(),
    val isEditMode: Boolean = false,
    val errorMessage: String? = null,
    val projectColor: Color? = null,
)