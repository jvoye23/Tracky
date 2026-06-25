@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.features.project_archive.presentation.project_archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.jvcs.tracky.core.presentation.model.ProjectUi
import com.jvcs.tracky.design_system.components.MainNavDrawerItem
import com.jvcs.tracky.design_system.components.MainNavigationDrawer
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.util.DevicePreviews
import com.jvcs.tracky.features.project_archive.presentation.project_archive.components.ProjectArchiveTopBar
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.ProjectCard
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProjectArchiveScreenRoot(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToProjects: () -> Unit,
    viewModel: ProjectArchiveViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProjectArchiveScreen(
        state = state,
        onAction = { action ->
            when (action) {
                is ProjectArchiveAction.OnProjectCardClick -> onNavigateToDetail(action.projectId)
                else -> Unit
            }
            viewModel.onAction(action)
        },
        onNavigateToProjects = onNavigateToProjects
    )
}

@Composable
fun ProjectArchiveScreen(
    state: ProjectArchiveState,
    onAction: (ProjectArchiveAction) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToProjects: () -> Unit = {},
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed)
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val drawerScope = rememberCoroutineScope()
    val backState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = backState,
        isBackEnabled = drawerState.isOpen,
        onBackCompleted = {
            drawerScope.launch { drawerState.close() }
        }
    )

    MainNavigationDrawer(
        drawerState = drawerState,
        selectedItem = MainNavDrawerItem.ARCHIVE,
        onProjectsClick = onNavigateToProjects
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                ProjectArchiveTopBar(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    state = state,
                    onAction = onAction,
                    onMenuClick = { drawerScope.launch { drawerState.open() } },
                    scrollBehavior = scrollBehavior
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp)
                    .testTag("project_archive"),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.filteredProjects ?: emptyList(),
                    key = { it.projectId }
                ) { item ->
                    ProjectCard(
                        modifier = Modifier.animateItem(),
                        projectUi = item,
                        onClick = { onAction(ProjectArchiveAction.OnProjectCardClick(item.projectId)) }
                    )
                }
            }
        }
    }
}

private fun previewArchivedProjects(): List<ProjectUi> = listOf(
    ProjectUi(
        projectId = "1",
        title = "Old Marketing Site",
        description = "Archived after launch",
        color = Color(0xFF9C27B0),
        totalDuration = "12h 0m",
        startDateTimeUtc = "Jan, 5, 2025",
        isFinished = true,
        endDateTimeUtc = "Mar, 1, 2025",
        projectTasks = emptyList()
    ),
    ProjectUi(
        projectId = "2",
        title = "Legacy API",
        description = "No longer maintained",
        color = Color(0xFF607D8B),
        totalDuration = "40h 30m",
        startDateTimeUtc = "Feb, 2, 2024",
        isFinished = true,
        endDateTimeUtc = "Dec, 1, 2024",
        projectTasks = emptyList()
    )
)

@DevicePreviews
@Composable
private fun ProjectArchiveDefaultPreview() {
    TrackyTheme {
        ProjectArchiveScreen(
            state = ProjectArchiveState(
                projects = previewArchivedProjects(),
                filteredProjects = previewArchivedProjects()
            ),
            onAction = {}
        )
    }
}

@DevicePreviews
@Composable
private fun ProjectArchiveEmptyPreview() {
    TrackyTheme {
        ProjectArchiveScreen(
            state = ProjectArchiveState(
                projects = emptyList(),
                filteredProjects = emptyList()
            ),
            onAction = {}
        )
    }
}

@DevicePreviews
@Composable
private fun ProjectArchiveSearchPreview() {
    TrackyTheme {
        ProjectArchiveScreen(
            state = ProjectArchiveState(
                projects = previewArchivedProjects(),
                filteredProjects = previewArchivedProjects().filter { it.title.contains("Legacy", ignoreCase = true) },
                isSearchActive = true,
                searchQuery = "Legacy"
            ),
            onAction = {}
        )
    }
}
