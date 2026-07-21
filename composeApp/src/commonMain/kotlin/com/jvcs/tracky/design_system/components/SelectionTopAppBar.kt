@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.design_system.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.jvcs.tracky.design_system.Icon_Archive
import com.jvcs.tracky.design_system.Icon_Delete
import com.jvcs.tracky.design_system.Icon_File_Export
import com.jvcs.tracky.design_system.Icon_Pin
import com.jvcs.tracky.design_system.theme.TrackyTheme
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.archive_selected
import tracky.composeapp.generated.resources.delete_selected
import tracky.composeapp.generated.resources.exit_edit_mode
import tracky.composeapp.generated.resources.file_export_selected
import tracky.composeapp.generated.resources.pin_selected

@Composable
fun SelectionTopAppBar(
    selectedCount: Int,
    onExit: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = selectedCount.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.exit_edit_mode),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        scrollBehavior = scrollBehavior
    )
}

@Preview(showSystemUi = true, device = Devices.PIXEL_9_PRO)
@Composable
private fun SelectionTopAppBarPreview() {
    TrackyTheme {
        SelectionTopAppBar(
            selectedCount = 5,
            onExit = {},
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icon_Pin,
                        contentDescription = stringResource(Res.string.pin_selected),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icon_Archive,
                        contentDescription = stringResource(Res.string.archive_selected),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icon_Delete,
                        contentDescription = stringResource(Res.string.delete_selected),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                //TODO("Implement Export action")
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icon_File_Export,
                        contentDescription = stringResource(Res.string.file_export_selected),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
    }
}