@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.jvcs.tracky.features.project.presentation.project_archive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import com.jvcs.tracky.design_system.Icon_Trash
import com.jvcs.tracky.design_system.components.MainNavDrawerItem
import com.jvcs.tracky.design_system.components.MainNavigationDrawer
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.util.DevicePreviews
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import com.jvcs.tracky.features.project.presentation.project_archive.components.ProjectArchiveSearchTopAppBar
import com.jvcs.tracky.features.project.presentation.project_archive.components.ProjectArchiveSelectionTopAppBar
import com.jvcs.tracky.features.project.presentation.project_overview.components.ProjectCard
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.archive_title
import tracky.composeapp.generated.resources.cancel
import tracky.composeapp.generated.resources.confirm
import tracky.composeapp.generated.resources.delete_one_project_confirmation
import tracky.composeapp.generated.resources.delete_project_title
import tracky.composeapp.generated.resources.delete_projects_confirmation
import tracky.composeapp.generated.resources.delete_projects_title
import tracky.composeapp.generated.resources.delete_selected

@Composable
fun ProjectArchiveScreenRoot(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToTrash: () -> Unit,
    viewModel: ProjectArchiveViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ProjectArchiveEvent.ReactivateError -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Failed to reactivate all selected projects",
                        duration = SnackbarDuration.Long
                    )
                }
            }
        }
    }

    BackHandler(enabled = state.isEditModeActive) {
        viewModel.onAction(ProjectArchiveAction.OnExitEditMode)
    }

    ProjectArchiveScreen(
        state = state,
        onAction = { action ->
            when (action) {
                is ProjectArchiveAction.OnProjectCardClick -> {
                    if (!state.isEditModeActive) onNavigateToDetail(action.projectId)
                }
                else -> Unit
            }
            viewModel.onAction(action)
        },
        onNavigateToProjects = onNavigateToProjects,
        onNavigateToTrash = onNavigateToTrash,
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun ProjectArchiveScreen(
    state: ProjectArchiveState,
    onAction: (ProjectArchiveAction) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToProjects: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed)
) {
    val listState = rememberLazyListState()
    // Without this the bar collapses on any drag, even when the whole list fits on screen and
    // there is nothing to scroll to.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        canScroll = { listState.canScrollForward || listState.canScrollBackward }
    )

    // If the content shrinks below one screen while the bar is collapsed, no scroll is left to
    // bring it back, so release it explicitly.
    LaunchedEffect(scrollBehavior) {
        snapshotFlow { listState.canScrollForward || listState.canScrollBackward }
            .collect { scrollable -> if (!scrollable) scrollBehavior.state.heightOffset = 0f }
    }
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
        onProjectsClick = onNavigateToProjects,
        onTrashClick = onNavigateToTrash
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                AnimatedContent(
                    targetState = state.isEditModeActive,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "topBarSwap"
                ) { editMode ->
                    if (editMode) {
                        ProjectArchiveSelectionTopAppBar(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            state = state,
                            onAction = onAction,
                            scrollBehavior = scrollBehavior
                        )
                    } else {
                        ProjectArchiveSearchTopAppBar(
                            title = stringResource(Res.string.archive_title),
                            modifier = Modifier.padding(horizontal = 10.dp),
                            state = state,
                            onAction = onAction,
                            onMenuClick = { drawerScope.launch { drawerState.open() } },
                            scrollBehavior = scrollBehavior,
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            LazyColumn(
                state = listState,
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
                        onClick = { onAction(ProjectArchiveAction.OnProjectCardClick(item.projectId)) },
                        onLongClick = { onAction(ProjectArchiveAction.OnProjectCardLongPress(item.projectId)) },
                        onToggleSelection = { onAction(ProjectArchiveAction.OnProjectCardToggleSelection(item.projectId)) },
                        isEditModeActive = state.isEditModeActive,
                        isSelected = item.projectId in state.selectedProjectIds
                    )
                }
            }
            if (state.isDeleteConfirmationDialogVisible) {
                AlertDialog(
                    onDismissRequest = { onAction(ProjectArchiveAction.OnDismissDeleteDialog) },
                    icon = {
                        Icon(
                            imageVector = Icon_Trash,
                            contentDescription = stringResource(Res.string.delete_selected),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    title = {
                        Text(
                            text = if (state.selectedProjectIds.size != 1)
                                stringResource(Res.string.delete_projects_title)
                            else stringResource(Res.string.delete_project_title)
                        )
                    },
                    text = {
                        Text(
                            text = if (state.selectedProjectIds.size != 1)
                                stringResource(
                                    Res.string.delete_projects_confirmation,
                                    state.selectedProjectIds.size
                                ) else stringResource(Res.string.delete_one_project_confirmation)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { onAction(ProjectArchiveAction.OnConfirmDelete) }) {
                            Text(text = stringResource(Res.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { onAction(ProjectArchiveAction.OnDismissDeleteDialog) }) {
                            Text(text = stringResource(Res.string.cancel))
                        }
                    }
                )
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
                isSearchActive = true,
                searchQuery = "Legacy"
            ),
            onAction = {}
        )
    }
}
