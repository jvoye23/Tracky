@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.features.project_trash.presentation.project_trash.components

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
import com.jvcs.tracky.features.project_trash.presentation.project_trash.ProjectTrashAction
import com.jvcs.tracky.features.project_trash.presentation.project_trash.ProjectTrashState
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.delete_permanently
import tracky.composeapp.generated.resources.restore_selected

@Composable
fun ProjectTrashSelectionTopAppBar(
    modifier: Modifier = Modifier,
    state: ProjectTrashState,
    onAction: (ProjectTrashAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    SelectionTopAppBar(
        selectedCount = state.selectedProjectIds.size,
        onExit = { onAction(ProjectTrashAction.OnExitEditMode)},
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = { onAction(ProjectTrashAction.OnDeleteSelectedClick) }) {
                Icon(
                    imageVector = Icon_Delete,
                    contentDescription = stringResource(Res.string.delete_permanently),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { onAction(ProjectTrashAction.OnRestoreSelectedClick) }) {
                Icon(
                    imageVector = Icon_Archive,
                    contentDescription = stringResource(Res.string.restore_selected),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.rotate(180f)
                )
            }
        },
        modifier = modifier
    )
}

@Preview(showSystemUi = true, device = Devices.PIXEL_9_PRO)
@Composable
private fun ProjectTrashSelectionTopAppBarPreview() {
    TrackyTheme {
        ProjectTrashSelectionTopAppBar(
            state = ProjectTrashState(
                isEditModeActive = true
            ),
            onAction = {},
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        )
    }
}