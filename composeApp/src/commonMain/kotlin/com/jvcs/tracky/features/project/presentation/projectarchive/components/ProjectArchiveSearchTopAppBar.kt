@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.features.project.presentation.projectarchive.components

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
import com.jvcs.tracky.designsystem.components.SearchTopAppBar
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.projectarchive.ProjectArchiveAction
import com.jvcs.tracky.features.project.presentation.projectarchive.ProjectArchiveState
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.archive_title
import tracky.composeapp.generated.resources.navigation_menu
import tracky.composeapp.generated.resources.search_in_archive

@Composable
fun ProjectArchiveSearchTopAppBar(
    title: String?,
    isSearchActive: Boolean,
    searchQuery: String,
    onAction: (ProjectArchiveAction) -> Unit,
    onMenuClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    SearchTopAppBar(
        title = title,
        isSearchBoxExpanded = isSearchActive,
        searchHint = stringResource(Res.string.search_in_archive),
        searchQuery = searchQuery,
        onQueryChange = { onAction(ProjectArchiveAction.OnSearchQueryChange(it)) },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(Res.string.navigation_menu),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = { onAction(ProjectArchiveAction.OnToggleSearch) }) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = stringResource(Res.string.search_in_archive),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        modifier = modifier,
    )
}

@Preview(showSystemUi = true, device = Devices.PIXEL_9_PRO)
@Composable
private fun SearchTopBarPreview() {
    TrackyTheme {
        ProjectArchiveSearchTopAppBar(
            title = stringResource(Res.string.archive_title),
            onAction = {},
            isSearchActive = false,
            searchQuery = "",
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
            title = stringResource(Res.string.archive_title),
            onAction = {},
            isSearchActive = true,
            searchQuery = "",
            onMenuClick = {},
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
        )
    }
}
