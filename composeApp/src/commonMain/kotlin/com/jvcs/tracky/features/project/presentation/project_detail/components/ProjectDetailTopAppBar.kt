package com.jvcs.tracky.features.project.presentation.project_detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import com.jvcs.tracky.features.project.presentation.project_detail.ProjectDetailAction
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.cancel
import tracky.composeapp.generated.resources.edit_uppercase
import tracky.composeapp.generated.resources.save
import tracky.composeapp.generated.resources.title


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailTopAppBar(
    modifier: Modifier = Modifier,
    isEditMode: Boolean,
    onAction: (ProjectDetailAction) -> Unit,
    project: ProjectUi?,
) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    // TODO() Replace DateTime string to date set in Agenda Screen
                    text = if (isEditMode){
                        stringResource(Res.string.edit_uppercase) + " " + project?.title?.uppercase()
                    } else project?.title?.uppercase() ?: stringResource(Res.string.title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        navigationIcon = {
            if (isEditMode) {
                TextButton(
                    onClick = { onAction(ProjectDetailAction.OnCloseAndCancelClick) },
                    modifier = Modifier,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.cancel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                IconButton(
                    modifier = Modifier
                        .size(40.dp),
                    onClick = {
                        onAction(ProjectDetailAction.OnCloseAndCancelClick)
                    },
                    content = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                )
            }
        },
        actions = {
            if (isEditMode) {
                TextButton(
                    onClick = { onAction(ProjectDetailAction.OnSaveClick) } ,
                    modifier = Modifier,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.save),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(
                    modifier = Modifier
                        .size(40.dp),
                    onClick = {
                        onAction(ProjectDetailAction.OnEditModeClick)
                    },

                    content = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                )
            }
        },
        colors = TopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )


}