package com.jvcs.tracky.features.project.presentation.project_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project.domain.project.EditTextType
import com.jvcs.tracky.features.project.presentation.project_detail.components.EditTextTopAppBar

import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.description
import tracky.composeapp.generated.resources.title

@Composable
fun EditTextScreenRoot(
    editTextType: EditTextType,
    editText: String?,
    onCancelClick: () -> Unit,
    onSaveClick: (String, EditTextType) -> Unit
) {
    EditTextScreen(
        editTextType = editTextType,
        editText = editText,
        onCancelClick = onCancelClick,
        onSaveClick = onSaveClick
    )
}

@Composable
private fun EditTextScreen(
    editTextType: EditTextType,
    editText: String?,
    onCancelClick: () -> Unit,
    onSaveClick: (updatedText: String, editTextType: EditTextType) -> Unit
) {
    var currentText by rememberSaveable { mutableStateOf(
        editText ?: ""
    )}

    val focusRequester = remember { FocusRequester() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            EditTextTopAppBar(
                onCancelClick = onCancelClick,
                onSaveClick = { onSaveClick(currentText.toString(), editTextType) },
                title = when(editTextType) {
                    EditTextType.TITLE -> stringResource(Res.string.title)
                    EditTextType.DESCRIPTION -> stringResource(Res.string.description)
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
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
                value = currentText,
                onValueChange = { currentText = it },
                placeholder = {
                    Text(
                        text = when(editTextType) {
                            EditTextType.TITLE -> stringResource(Res.string.title)
                            EditTextType.DESCRIPTION -> stringResource(Res.string.description)
                        },
                        style = when(editTextType) {
                            EditTextType.TITLE -> MaterialTheme.typography.titleLarge
                            EditTextType.DESCRIPTION -> MaterialTheme.typography.bodyMedium
                        }
                    )
                },
                textStyle = when(editTextType) {
                    EditTextType.TITLE -> MaterialTheme.typography.titleLarge
                    EditTextType.DESCRIPTION -> MaterialTheme.typography.bodyMedium
                },
                shape = RectangleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
                    disabledIndicatorColor = MaterialTheme.colorScheme.surface,
                    errorIndicatorColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                singleLine = editTextType == EditTextType.TITLE,
                modifier = if (editTextType == EditTextType.TITLE) {
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                } else Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
            )
        }
    }
    LaunchedEffect(Unit) {
        // A small delay to ensure the UI is fully drawn before the focus is requested
        delay(100)
        if (editText == null) {
            focusRequester.requestFocus()
        }
    }
}

@Preview
@Composable
private fun EditTextScreenPreview() {
    TrackyTheme {
        EditTextScreen(
            editTextType = EditTextType.DESCRIPTION,
            editText = "My Title",
            onCancelClick = {},
            onSaveClick = {_, _ -> }
        )
    }
}