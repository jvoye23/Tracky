package com.jvcs.tracky.features.project.presentation.project_detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.project_detail.ProjectDetailAction
import com.jvcs.tracky.features.project.presentation.project_detail.ProjectDetailState
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.create_new_task
import tracky.composeapp.generated.resources.enter_new_title


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewProjectTaskBottomSheet(
    modifier: Modifier = Modifier,
    state: ProjectDetailState,
    onAction: (ProjectDetailAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = { onAction(ProjectDetailAction.OnToggleAddNewProjectSessionBottomSheet) },
        modifier = modifier.fillMaxWidth(),
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SheetContent(
            state = state,
            onAction = onAction,
        )
    }
}

@Composable
private fun SheetContent(
    modifier: Modifier = Modifier,
    state: ProjectDetailState,
    onAction: (ProjectDetailAction) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .padding(horizontal = 16.dp)
    ) {

        TextField(
            state = state.addProjectTaskTextFieldState,
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .onFocusChanged {
                    it.isFocused
                },
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.secondary
            ),
            placeholder = {
                Text(
                    text = stringResource(Res.string.enter_new_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.7f
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },

            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text

            ),
            shape = RoundedCornerShape(50.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceDim,
                focusedTextColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(24.dp))


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier
                    .padding(16.dp),
                onClick = {
                    onAction(ProjectDetailAction.OnCreateProjectSession(projectSessionTitle = state.addProjectTaskTextFieldState.text as String))
                }
            ) {
                Text(
                    modifier = Modifier
                        .padding(16.dp),
                    text = stringResource(Res.string.create_new_task),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )

            }

        }

    }
}

@Preview
@Composable
private fun SheetContentPreview() {
    TrackyTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            SheetContent(
                modifier = Modifier,
                onAction = {},
                state = ProjectDetailState()
            )
        }

    }

}