package com.jvcs.tracky.features.project_tracker.presentation.project_overview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewAction
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewState
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.add_project
import tracky.composeapp.generated.resources.enter_new_title


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewProjectBottomSheet(
    modifier: Modifier = Modifier,
    state: ProjectOverviewState,
    onAction: (ProjectOverviewAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = { onAction(ProjectOverviewAction.OnToggleAddNewProjectBottomSheet) },
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
    state: ProjectOverviewState,
    onAction: (ProjectOverviewAction) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .padding(horizontal = 16.dp)
    ) {
        /*Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(
                modifier = Modifier
                    .align(Alignment.Center),
                text = stringResource(Res.string.add_project).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterEnd),
                onClick = { onAction(ProjectOverviewAction.OnToggleAddNewProjectBottomSheet) },
                content = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }*/

        //Spacer(modifier = Modifier.height(28.dp))
        TextField(
            state = state.addProjectTextFieldState,
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
                    text = stringResource(Res.string.enter_new_title).uppercase(),
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
        /*Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceDim)
        )*/

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
                    onAction(ProjectOverviewAction.OnAddProjectClick(projectTitle = state.addProjectTextFieldState.text as String))
                }
            ) {
                Text(
                    modifier = Modifier
                        .padding(16.dp),
                    text = "Create project",
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
                state = ProjectOverviewState()
            )
        }

    }

}