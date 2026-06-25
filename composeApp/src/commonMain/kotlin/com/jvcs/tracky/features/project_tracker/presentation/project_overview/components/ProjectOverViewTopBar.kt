@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.features.project_tracker.presentation.project_overview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.design_system.Icon_Swap_Vert_Down
import com.jvcs.tracky.design_system.Icon_Swap_Vert_Up
import com.jvcs.tracky.design_system.components.UserProfileButton
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewAction
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewState
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.SortOption
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.search_in_projects
import tracky.composeapp.generated.resources.select_color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectOverViewTopBar(
    modifier: Modifier = Modifier,
    onAction: (ProjectOverviewAction) -> Unit,
    state: ProjectOverviewState,
    scrollBehavior: TopAppBarScrollBehavior,
    onLogout: () -> Unit,
    onMenuClick: () -> Unit,
    username: String?,
    email: String?,
) {
    TopAppBar(
        title = {
            Surface(
                modifier = Modifier
                    .height(48.dp)
                    .padding(horizontal = 8.dp)
                    .shadow(2.dp, CircleShape),
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
                        onValueChange = { onAction(ProjectOverviewAction.OnSearchQueryChange(it)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (state.searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.search_in_projects),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    val isSortActive = state.sortOption != SortOption.CUSTOM
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSortActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else Color.Transparent
                            )
                            .clickable { onAction(ProjectOverviewAction.OnToggleSortBottomSheet) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icon_Swap_Vert_Up,
                            contentDescription = "Sort",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icon_Swap_Vert_Down,
                            contentDescription = "Sort",
                            modifier = Modifier.size(20.dp),
                            tint = if (isSortActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
            if (username != null && email != null) {
                UserProfileButton(
                    username = username,
                    email = email,
                    onLogoutClick = onLogout,
                    modifier = Modifier.padding(start = 8.dp)
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
private fun ProjectOverviewTopBarPreview() {
    TrackyTheme {
        ProjectOverViewTopBar(
            onAction = {},
            state = ProjectOverviewState(),
            onLogout = {},
            onMenuClick = {},
            username = "Jay V",
            email = "j.voye@jv-coding-solutions.com",
            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        )
    }
}
