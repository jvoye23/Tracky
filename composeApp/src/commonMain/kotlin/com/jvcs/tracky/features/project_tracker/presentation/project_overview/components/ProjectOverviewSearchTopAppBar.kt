@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.features.project_tracker.presentation.project_overview.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.design_system.components.SearchTopAppBar
import com.jvcs.tracky.design_system.components.UserProfileButton
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewAction
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewState
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.search_in_projects

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectOverviewSearchTopAppBar(
    modifier: Modifier = Modifier,
    onAction: (ProjectOverviewAction) -> Unit,
    state: ProjectOverviewState,
    scrollBehavior: TopAppBarScrollBehavior,
    onMenuClick: () -> Unit,
    username: String?,
    email: String?,
) {
    SearchTopAppBar(
        isSearchBoxExpanded = true ,
        searchHint = stringResource(Res.string.search_in_projects),
        searchQuery = state.searchQuery,
        onQueryChange = { onAction(ProjectOverviewAction.OnSearchQueryChange(it)) },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        sortOption = state.sortOption,
        onToggleSortBottomSheet = { onAction(ProjectOverviewAction.OnToggleSortBottomSheet) },
        scrollBehavior = scrollBehavior,
        actions = {
            if (username != null && email != null) {
                UserProfileButton(
                    username = username,
                    email = email,
                    onLogoutClick = { onAction(ProjectOverviewAction.OnLogoutClick) },
                    modifier = Modifier.padding(start = 8.dp, end = 10.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
    )
}

@Preview(showSystemUi = true, device = Devices.PIXEL_9_PRO)
@Composable
private fun ProjectOverviewTopBarPreview() {
    TrackyTheme {
        ProjectOverviewSearchTopAppBar(
            onAction = {},
            state = ProjectOverviewState(),
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
            onMenuClick = {},
            username = "Jörg Voyé",
            email = "j.voye@jv-coding-solutions.com"
        )
    }
}