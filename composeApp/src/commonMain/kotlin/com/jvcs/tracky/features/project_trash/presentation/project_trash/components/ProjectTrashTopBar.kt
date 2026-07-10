@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.features.project_trash.presentation.project_trash.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project_trash.presentation.project_trash.ProjectTrashAction
import com.jvcs.tracky.features.project_trash.presentation.project_trash.ProjectTrashState
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.search_in_trash
import tracky.composeapp.generated.resources.trash_title

@Composable
fun ProjectTrashTopBar(
    modifier: Modifier = Modifier,
    state: ProjectTrashState,
    onAction: (ProjectTrashAction) -> Unit,
    onMenuClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            if (state.isSearchActive) {
                val focusRequester = remember { FocusRequester() }
                // Auto-focus the field (and raise the keyboard) when search opens.
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Surface(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = { onAction(ProjectTrashAction.OnSearchQueryChange(it)) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (state.searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(Res.string.search_in_trash),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(Res.string.trash_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            IconButton(onClick = { onAction(ProjectTrashAction.OnToggleSearch) }) {
                Icon(
                    imageVector = if (state.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search",
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

@Preview(showSystemUi = true, device = Devices.PIXEL_9_PRO)
@Composable
private fun ProjectTrashTopBarPreview() {
    TrackyTheme {
        ProjectTrashTopBar(
            state = ProjectTrashState(),
            onAction = {},
            onMenuClick = {},
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        )
    }
}

@Preview(showSystemUi = true, device = Devices.PIXEL_9_PRO)
@Composable
private fun ProjectTrashTopBarSearchPreview() {
    TrackyTheme {
        ProjectTrashTopBar(
            state = ProjectTrashState(isSearchActive = true, searchQuery = "Design"),
            onAction = {},
            onMenuClick = {},
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        )
    }
}
