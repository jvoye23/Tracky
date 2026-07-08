@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.jvcs.tracky.features.project_tracker.presentation.project_overview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
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
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.jvcs.tracky.core.presentation.model.ProjectTaskUi
import com.jvcs.tracky.core.presentation.model.ProjectUi
import com.jvcs.tracky.design_system.Icon_Delete
import com.jvcs.tracky.design_system.components.MainNavDrawerItem
import com.jvcs.tracky.design_system.components.MainNavigationDrawer
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.util.DevicePreviews
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import com.jvcs.tracky.design_system.util.UiText
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.AddNewProjectBottomSheet
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.ProjectCard
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.ProjectOverViewTopBar
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.ProjectOverviewEditModeTopBar
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.SortBottomSheet
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.SortSheetContent
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.cancel
import tracky.composeapp.generated.resources.confirm
import tracky.composeapp.generated.resources.current_projects
import tracky.composeapp.generated.resources.delete_one_project_confirmation
import tracky.composeapp.generated.resources.delete_project_title
import tracky.composeapp.generated.resources.delete_projects_confirmation
import tracky.composeapp.generated.resources.delete_projects_title
import tracky.composeapp.generated.resources.delete_selected
import tracky.composeapp.generated.resources.new_project
import tracky.composeapp.generated.resources.project_saved_successfully
import tracky.composeapp.generated.resources.search_results

@Composable
fun ProjectOverviewScreenRoot(
    username: String?,
    email: String?,
    onLogout: () -> Unit,
    onNavigateToDetailScreen: (String) -> Unit,
    onNavigateToArchive: () -> Unit = {},
    viewModel: ProjectOverviewViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when(event) {
            is ProjectOverviewEvent.Error -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.error.toString(),
                        duration = SnackbarDuration.Short
                    )
                }
            }
            is ProjectOverviewEvent.ArchiveError -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Failed to archive all selected projects",
                        duration = SnackbarDuration.Long
                    )
                }
            }
            is ProjectOverviewEvent.NewProjectSaved -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Project saved successfully!",
                        duration = SnackbarDuration.Long
                    )
                }
                onNavigateToDetailScreen(event.projectId)
            }
        }
    }

    BackHandler(enabled = state.isEditModeActive) {
        viewModel.onAction(ProjectOverviewAction.OnExitEditMode)
    }

    ProjectOverviewScreen(
        onAction = { action ->
            when(action) {
                is ProjectOverviewAction.OnProjectCardClick -> {
                    if (!state.isEditModeActive) onNavigateToDetailScreen(action.projectId)
                }
                else -> Unit
            }
            viewModel.onAction(action)
        },
        state = state,
        username = username,
        email = email,
        onLogout = onLogout,
        onNavigateToArchive = onNavigateToArchive,
        snackbarHostState = snackbarHostState,
        sortOption = sortOption
    )
}

@Composable
fun ProjectOverviewScreen(
    onAction: (ProjectOverviewAction) -> Unit,
    state: ProjectOverviewState,
    username: String?,
    email: String?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToArchive: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
    sortOption: SortOption = SortOption.CUSTOM,
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
        selectedItem = MainNavDrawerItem.PROJECTS,
        onArchiveClick = onNavigateToArchive
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
                    ProjectOverviewEditModeTopBar(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        state = state,
                        onAction = onAction,
                        scrollBehavior = scrollBehavior
                    )
                } else {
                    ProjectOverViewTopBar(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        onAction = onAction,
                        state = state,
                        onLogout = onLogout,
                        onMenuClick = { drawerScope.launch { drawerState.open() } },
                        username = username,
                        email = email,
                        scrollBehavior = scrollBehavior
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    onAction(ProjectOverviewAction.OnFabClick)
                },

            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(Res.string.new_project),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
                .testTag("project_overview"),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    modifier = Modifier
                        .padding(bottom = 8.dp, top = 8.dp, start = 8.dp),
                    text = if (state.searchQuery.isEmpty()) stringResource(Res.string.current_projects) else stringResource(Res.string.search_results),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(
                items = state.filteredProjects ?: emptyList(),
                key = { it.projectId }
            ) { item ->
                ProjectCard(
                    modifier = Modifier.animateItem(),
                    projectUi = item,
                    onClick = { onAction(ProjectOverviewAction.OnProjectCardClick(item.projectId)) },
                    onLongClick = { onAction(ProjectOverviewAction.OnProjectCardLongPress(item.projectId)) },
                    onToggleSelection = { onAction(ProjectOverviewAction.OnProjectCardToggleSelection(item.projectId)) },
                    isEditModeActive = state.isEditModeActive,
                    isSelected = item.projectId in state.selectedProjectIds
                )

            }

        }
        if (state.isAddNewProjectBottomSheetVisible) {
            AddNewProjectBottomSheet(
                state = state,
                onAction = onAction
            )
        }
        if (state.isSortBottomSheetVisible) {
            SortBottomSheet(
                sortOption = sortOption,
                onAction = onAction
            )
        }
        if (state.isDeleteConfirmationDialogVisible) {
            AlertDialog(
                onDismissRequest = { onAction(ProjectOverviewAction.OnDismissDeleteDialog) },
                icon = {
                    Icon(
                        imageVector = Icon_Delete,
                        contentDescription = stringResource(Res.string.delete_selected),
                        tint = MaterialTheme.colorScheme.onSurface
                    )

                },
                title = {
                    Text(
                        text = if(state.selectedProjectIds.size != 1)
                            stringResource(Res.string.delete_projects_title)
                        else stringResource(Res.string.delete_project_title)
                    )
                },
                text = {
                    Text(
                        text = if(state.selectedProjectIds.size != 1)
                            stringResource(
                            Res.string.delete_projects_confirmation,
                            state.selectedProjectIds.size
                        ) else stringResource(Res.string.delete_one_project_confirmation)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onAction(ProjectOverviewAction.OnConfirmDelete) }) {
                        Text(text = stringResource(Res.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onAction(ProjectOverviewAction.OnDismissDeleteDialog) }) {
                        Text(text = stringResource(Res.string.cancel))
                    }
                }
            )
        }
    }
    }
}

private fun previewProjects(): List<ProjectUi> = listOf(
    ProjectUi(
        projectId = "1",
        title = "Running Project",
        description = "Currently tracking time",
        color = Color(0xFF4CAF50),
        totalDuration = "2h 30m",
        startDateTimeUtc = "2025-12-01T10:00",
        isFinished = false,
        endDateTimeUtc = null,
        projectTasks = listOf(
            ProjectTaskUi(
                id = "t1",
                title = "Task 1",
                formattedDuration = "2h 30m",
                formattedStateDateTime = "10:00",
                formattedEndDateTimeUtc = "",
                isTimerRunning = true
            )
        )
    ),
    ProjectUi(
        projectId = "2",
        title = "Completed Project",
        description = "All tasks done",
        color = Color(0xFF2196F3),
        totalDuration = "5h 15m",
        startDateTimeUtc = "2025-11-20T09:00",
        isFinished = true,
        endDateTimeUtc = "2025-11-25T17:00",
        projectTasks = listOf(
            ProjectTaskUi(
                id = "t2",
                title = "Task 2",
                formattedDuration = "5h 15m",
                formattedStateDateTime = "09:00",
                formattedEndDateTimeUtc = "17:00",
                isTimerRunning = false
            )
        )
    ),
    ProjectUi(
        projectId = "3",
        title = "Idle Project",
        description = "Not started yet",
        color = Color(0xFFFFC107),
        totalDuration = "0h 0m",
        startDateTimeUtc = "2025-12-05T08:00",
        isFinished = false,
        endDateTimeUtc = null,
        projectTasks = emptyList()
    )
)

@DevicePreviews
@Composable
private fun ProjectOverviewDefaultPreview() {
    TrackyTheme {
        ProjectOverviewScreen(
            onAction = {},
            state = ProjectOverviewState(
                projects = previewProjects(),
                filteredProjects = previewProjects()
            ),
            username = "JoergVoye",
            email = "joerg@example.com",
            onLogout = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}

@DevicePreviews
@Composable
private fun ProjectOverviewSearchPreview() {
    TrackyTheme {
        ProjectOverviewScreen(
            onAction = {},
            state = ProjectOverviewState(
                projects = previewProjects(),
                filteredProjects = previewProjects().filter { it.title.contains("Run", ignoreCase = true) },
                searchQuery = "Run"
            ),
            username = "JoergVoye",
            email = "joerg@example.com",
            onLogout = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}

@DevicePreviews
@Composable
private fun ProjectOverviewEditModePreview() {
    TrackyTheme {
        ProjectOverviewScreen(
            onAction = {},
            state = ProjectOverviewState(
                projects = previewProjects(),
                filteredProjects = previewProjects(),
                isEditModeActive = true,
                selectedProjectIds = setOf("1", "3")
            ),
            username = "JoergVoye",
            email = "joerg@example.com",
            onLogout = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}

@DevicePreviews
@Composable
private fun ProjectOverviewDrawerOpenPreview() {
    TrackyTheme {
        ProjectOverviewScreen(
            onAction = {},
            state = ProjectOverviewState(
                projects = previewProjects(),
                filteredProjects = previewProjects()
            ),
            username = "JoergVoye",
            email = "joerg@example.com",
            onLogout = {},
            snackbarHostState = remember { SnackbarHostState() },
            drawerState = rememberDrawerState(DrawerValue.Open)
        )
    }
}

@DevicePreviews
@Composable
private fun ProjectOverviewSortSheetVisiblePreview() {
    TrackyTheme {
        // ModalBottomSheet does not render in static previews, so the sort sheet
        // content is overlaid at the bottom to approximate the visible state.
        Box(modifier = Modifier.fillMaxSize()) {
            ProjectOverviewScreen(
                onAction = {},
                state = ProjectOverviewState(
                    projects = previewProjects(),
                    filteredProjects = previewProjects()
                ),
                username = "JoergVoye",
                email = "joerg@example.com",
                onLogout = {},
                snackbarHostState = remember { SnackbarHostState() }
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 8.dp
            ) {
                SortSheetContent(
                    selectedOption = SortOption.CREATION_DATE,
                    onOptionSelected = {}
                )
            }
        }
    }
}