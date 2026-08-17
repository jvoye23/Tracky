@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.features.project.presentation.project_overview.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.jvcs.tracky.design_system.Icon_Archive
import com.jvcs.tracky.design_system.Icon_Delete
import com.jvcs.tracky.design_system.Icon_File_Export
import com.jvcs.tracky.design_system.Icon_Pin
import com.jvcs.tracky.design_system.components.SelectionTopAppBar
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.project_overview.ProjectOverviewAction
import com.jvcs.tracky.features.project.presentation.project_overview.ProjectOverviewState
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.archive_selected
import tracky.composeapp.generated.resources.delete_selected
import tracky.composeapp.generated.resources.file_export_selected
import tracky.composeapp.generated.resources.pin_selected

@Composable
fun ProjectOverviewSelectionTopAppBar(
    modifier: Modifier = Modifier,
    state: ProjectOverviewState,
    onAction: (ProjectOverviewAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    SelectionTopAppBar(
        selectedCount = state.selectedProjectIds.size,
        onExit = { onAction(ProjectOverviewAction.OnExitEditMode)},
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        actions = {
            IconButton(onClick = { onAction(ProjectOverviewAction.OnPinSelectedClick) }) {
                Icon(
                    imageVector = Icon_Pin,
                    contentDescription = stringResource(Res.string.pin_selected),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
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
        }
    )
}

@Preview(showSystemUi = true, device = Devices.PIXEL_9_PRO)
@Composable
private fun ProjectOverviewSelectionTopAppBarPreview() {
    TrackyTheme {
        ProjectOverviewSelectionTopAppBar(
            onAction = {},
            state = ProjectOverviewState(),
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        )
    }
}