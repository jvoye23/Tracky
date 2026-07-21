@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.features.project_archive.presentation.project_archive.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.jvcs.tracky.design_system.Icon_Archive
import com.jvcs.tracky.design_system.Icon_Delete
import com.jvcs.tracky.design_system.components.SelectionTopAppBar
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project_archive.presentation.project_archive.ProjectArchiveAction
import com.jvcs.tracky.features.project_archive.presentation.project_archive.ProjectArchiveState
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.delete_permanently
import tracky.composeapp.generated.resources.restore_selected

@Composable
fun ProjectArchiveSelectionTopAppBar(
    modifier: Modifier = Modifier,
    state: ProjectArchiveState,
    onAction: (ProjectArchiveAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    SelectionTopAppBar(
        selectedCount = state.selectedProjectIds.size,
        onExit = { onAction(ProjectArchiveAction.OnExitEditMode) },
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        actions = {
            IconButton(onClick = { onAction(ProjectArchiveAction.OnDeleteSelectedClick) }) {
                Icon(
                    imageVector = Icon_Delete,
                    contentDescription = stringResource(Res.string.delete_permanently),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { onAction(ProjectArchiveAction.OnReactivateSelectedClick) }) {
                Icon(
                    imageVector = Icon_Archive,
                    contentDescription = stringResource(Res.string.restore_selected),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.rotate(180f)
                )
            }
        }
    )
}

@Preview(showSystemUi = true, device = Devices.PIXEL_9_PRO)
@Composable
private fun TopAppBarPreview() {
    TrackyTheme {
        ProjectArchiveSelectionTopAppBar(
            state = ProjectArchiveState(),
            onAction = {},
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        )
    }
}