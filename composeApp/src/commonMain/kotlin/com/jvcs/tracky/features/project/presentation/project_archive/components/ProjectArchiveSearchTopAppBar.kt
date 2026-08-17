@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.features.project.presentation.project_archive.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import com.jvcs.tracky.design_system.components.SearchTopAppBar
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.project_archive.ProjectArchiveAction
import com.jvcs.tracky.features.project.presentation.project_archive.ProjectArchiveState
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.navigation_menu
import tracky.composeapp.generated.resources.search_in_archive
import tracky.composeapp.generated.resources.search_in_trash
import tracky.composeapp.generated.resources.trash_title

@Composable
fun ProjectArchiveSearchTopAppBar(
    title: String?,
    modifier: Modifier = Modifier,
    state: ProjectArchiveState,
    onAction: (ProjectArchiveAction) -> Unit,
    onMenuClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    SearchTopAppBar(
        title = title,
        isSearchBoxExpanded = state.isSearchActive,
        searchHint = stringResource(Res.string.search_in_archive),
        searchQuery = state.searchQuery,
        onQueryChange = { onAction(ProjectArchiveAction.OnSearchQueryChange(it)) },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(Res.string.navigation_menu),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = { onAction(ProjectArchiveAction.OnToggleSearch) }) {
                Icon(
                    imageVector = if (state.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = stringResource(Res.string.search_in_trash),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
    )
}

@Preview(showSystemUi = true, device = Devices.PIXEL_9_PRO)
@Composable
private fun SearchTopBarPreview() {
    TrackyTheme {
        ProjectArchiveSearchTopAppBar(
            title = stringResource(Res.string.trash_title),
            onAction = {},
            state = ProjectArchiveState(),
            onMenuClick = {},
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
        )
    }
}

@Preview(showSystemUi = true, device = Devices.PIXEL_9_PRO)
@Composable
private fun SearchActivePreview() {
    TrackyTheme {
        ProjectArchiveSearchTopAppBar(
            title = stringResource(Res.string.trash_title),
            onAction = {},
            state = ProjectArchiveState(
                isSearchActive = true
            ),
            onMenuClick = {},
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
        )
    }
}