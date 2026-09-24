@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.features.project.presentation.projecttrash.components

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
import com.jvcs.tracky.designsystem.Icon_Archive
import com.jvcs.tracky.designsystem.Icon_Delete
import com.jvcs.tracky.designsystem.components.SelectionTopAppBar
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.projecttrash.ProjectTrashAction
import com.jvcs.tracky.features.project.presentation.projecttrash.ProjectTrashState
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.delete_permanently
import tracky.composeapp.generated.resources.restore_selected

private const val UPSIDE_DOWN_DEGREES = 180f

@Composable
fun ProjectTrashSelectionTopAppBar(
    selectedCount: Int,
    onAction: (ProjectTrashAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    SelectionTopAppBar(
        selectedCount = selectedCount,
        onExit = { onAction(ProjectTrashAction.OnExitEditMode) },
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = { onAction(ProjectTrashAction.OnDeleteSelectedClick) }) {
                Icon(
                    imageVector = Icon_Delete,
                    contentDescription = stringResource(Res.string.delete_permanently),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = { onAction(ProjectTrashAction.OnRestoreSelectedClick) }) {
                Icon(
                    imageVector = Icon_Archive,
                    contentDescription = stringResource(Res.string.restore_selected),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.rotate(UPSIDE_DOWN_DEGREES),
                )
            }
        },
        modifier = modifier,
    )
}

@Preview(showSystemUi = true, device = Devices.PIXEL_9_PRO)
@Composable
private fun ProjectTrashSelectionTopAppBarPreview() {
    TrackyTheme {
        ProjectTrashSelectionTopAppBar(
            selectedCount = 0,
            onAction = {},
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
        )
    }
}
