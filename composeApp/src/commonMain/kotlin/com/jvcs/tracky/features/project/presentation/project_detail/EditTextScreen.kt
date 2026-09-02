package com.jvcs.tracky.features.project.presentation.project_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.project_detail.components.EditTextTopAppBar

import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.description
import tracky.composeapp.generated.resources.edit_project_uppercase
import tracky.composeapp.generated.resources.project_details_uppercase
import tracky.composeapp.generated.resources.title

@Composable
fun EditTextScreenRoot(
    isEditMode: Boolean,
    titleText: String,
    descriptionText: String,
    projectColor: Color?,
    onCancelClick: () -> Unit,
    onSaveClick: (title: String, description: String) -> Unit
) {
    EditTextScreen(
        isEditMode = isEditMode,
        titleText = titleText,
        descriptionText = descriptionText,
        projectColor = projectColor,
        onCancelClick = onCancelClick,
        onSaveClick = onSaveClick
    )
}

@Composable
private fun EditTextScreen(
    isEditMode: Boolean,
    titleText: String,
    descriptionText: String,
    projectColor: Color?,
    onCancelClick: () -> Unit,
    onSaveClick: (title: String, description: String) -> Unit
) {
    var currentTitle by rememberSaveable { mutableStateOf(titleText) }
    var currentDescription by rememberSaveable { mutableStateOf(descriptionText) }
    // Seeded from the caller, then owned here: the toolbar's edit button flips it without leaving
    // the screen, so arriving read-only and arriving ready to type are the same destination.
    var isEditing by rememberSaveable { mutableStateOf(isEditMode) }

    val focusRequester = remember { FocusRequester() }

    // The screen reads as one continuous document, so every indicator and container is painted in
    // the surface colour and the only visible separator is the divider between the two fields.
    val fieldColors = TextFieldDefaults.colors(
        focusedIndicatorColor = MaterialTheme.colorScheme.surface,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
        disabledIndicatorColor = MaterialTheme.colorScheme.surface,
        errorIndicatorColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            EditTextTopAppBar(
                isEditMode = isEditing,
                onCancelClick = onCancelClick,
                onEditClick = { isEditing = true },
                onSaveClick = { onSaveClick(currentTitle, currentDescription) },
                title = if (isEditing) stringResource(Res.string.edit_project_uppercase)
                else stringResource(Res.string.project_details_uppercase),
                // A project without a colour of its own falls back to the theme accent.
                projectColor = projectColor ?: MaterialTheme.colorScheme.primary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            OutlinedTextField(
                value = currentTitle,
                onValueChange = { currentTitle = it },
                readOnly = !isEditing,
                placeholder = {
                    Text(
                        text = stringResource(Res.string.title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                textStyle = MaterialTheme.typography.titleLarge,
                shape = RectangleShape,
                colors = fieldColors,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            OutlinedTextField(
                value = currentDescription,
                onValueChange = { currentDescription = it },
                readOnly = !isEditing,
                placeholder = {
                    Text(
                        text = stringResource(Res.string.description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RectangleShape,
                colors = fieldColors,
                singleLine = false,
                // weight, not fillMaxSize: a Column measures every child against the full incoming
                // height, so fillMaxSize here would push the field past the bottom of the screen
                // instead of letting it take what the title and divider leave over.
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
    LaunchedEffect(isEditing) {
        if (isEditing) {
            // A small delay to ensure the UI is fully drawn before the focus is requested
            delay(100)
            focusRequester.requestFocus()
        }
    }
}

@Preview
@Composable
private fun EditTextScreenReadModePreview() {
    TrackyTheme {
        EditTextScreen(
            isEditMode = false,
            projectColor = Color(0xFF4CAF50),
            titleText = "Tracky redesign",
            descriptionText = "Rebuild the onboarding flow so new users land on the project list " +
                    "instead of the empty timer screen.",
            onCancelClick = {},
            onSaveClick = { _, _ -> }
        )
    }
}

@Preview
@Composable
private fun EditTextScreenEditModePreview() {
    TrackyTheme {
        EditTextScreen(
            isEditMode = true,
            // No project colour: the bar falls back to the theme accent.
            projectColor = null,
            titleText = "Tracky redesign",
            descriptionText = "Rebuild the onboarding flow so new users land on the project list " +
                    "instead of the empty timer screen.",
            onCancelClick = {},
            onSaveClick = { _, _ -> }
        )
    }
}
