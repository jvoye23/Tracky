@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.features.project_tracker.presentation.project_overview.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jvcs.tracky.design_system.Icon_Archive
import com.jvcs.tracky.design_system.Icon_Delete
import com.jvcs.tracky.design_system.Icon_File_Export
import com.jvcs.tracky.design_system.Icon_Trash
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewAction
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewState
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.archive_selected
import tracky.composeapp.generated.resources.delete_selected
import tracky.composeapp.generated.resources.exit_edit_mode
import tracky.composeapp.generated.resources.file_export_selected

@Composable
fun ProjectOverviewEditModeTopBar(
    modifier: Modifier = Modifier,
    state: ProjectOverviewState,
    onAction: (ProjectOverviewAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = { onAction(ProjectOverviewAction.OnExitEditMode) }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.exit_edit_mode),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        title = {
            Text(
                text = state.selectedProjectIds.size.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        actions = {
            IconButton(onClick = { onAction(ProjectOverviewAction.OnArchiveSelectedClick) }) {
                Icon(
                    imageVector = Icon_Archive,
                    contentDescription = stringResource(Res.string.archive_selected),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { onAction(ProjectOverviewAction.OnDeleteSelectedClick) }) {
                Icon(
                    imageVector = Icon_Delete,
                    contentDescription = stringResource(Res.string.delete_selected),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            //TODO("Implement Export action")
            IconButton(onClick = {  }) {
                Icon(
                    imageVector = Icon_File_Export,
                    contentDescription = stringResource(Res.string.file_export_selected),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}
