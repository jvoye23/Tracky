@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.jvcs.tracky.features.project.presentation.project_overview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.jvcs.tracky.core.domain.auth.User
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import com.jvcs.tracky.design_system.Icon_Delete
import com.jvcs.tracky.design_system.components.FullScreenLoadingIndicator
import com.jvcs.tracky.design_system.components.MainNavDrawerItem
import com.jvcs.tracky.design_system.components.MainNavigationDrawer
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.util.DevicePreviews
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import com.jvcs.tracky.features.project.presentation.project_overview.components.AddNewProjectBottomSheet
import com.jvcs.tracky.features.project.presentation.project_overview.components.EmptySection
import com.jvcs.tracky.features.project.presentation.project_overview.components.ProjectCard
import com.jvcs.tracky.features.project.presentation.project_overview.components.ProjectDragDropState
import com.jvcs.tracky.features.project.presentation.project_overview.components.rememberProjectDragDropState
import com.jvcs.tracky.features.project.presentation.project_overview.components.ProjectOverviewSearchTopAppBar
import com.jvcs.tracky.features.project.presentation.project_overview.components.ProjectOverviewSelectionTopAppBar
import com.jvcs.tracky.features.project.presentation.project_overview.components.SortBottomSheet
import com.jvcs.tracky.features.project.presentation.project_overview.components.SortSheetContent
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.cancel
import tracky.composeapp.generated.resources.close
import tracky.composeapp.generated.resources.confirm
import tracky.composeapp.generated.resources.delete_one_project_confirmation
import tracky.composeapp.generated.resources.delete_project_title
import tracky.composeapp.generated.resources.delete_projects_confirmation
import tracky.composeapp.generated.resources.delete_projects_title
import tracky.composeapp.generated.resources.delete_selected
import tracky.composeapp.generated.resources.do_you_want_to_logout
import tracky.composeapp.generated.resources.do_you_want_to_logout_desc
import tracky.composeapp.generated.resources.error_archiving_projects
import tracky.composeapp.generated.resources.error_pinning_projects
import tracky.composeapp.generated.resources.log_out
import tracky.composeapp.generated.resources.logout_not_possible
import tracky.composeapp.generated.resources.logout_not_possible_desc
import tracky.composeapp.generated.resources.new_project
import tracky.composeapp.generated.resources.no_current_projects
import tracky.composeapp.generated.resources.no_current_projects_subtitle
import tracky.composeapp.generated.resources.no_internet_connection
import tracky.composeapp.generated.resources.other
import tracky.composeapp.generated.resources.pinned
import tracky.composeapp.generated.resources.project_saved_successfully
import tracky.composeapp.generated.resources.search_results

@Composable
fun ProjectOverviewScreenRoot(
    onSuccessfulLogout: () -> Unit,
    onNavigateToDetailScreen: (String) -> Unit,
    onNavigateToArchive: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
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
                        message = event.error.asStringAsync(),
                        duration = SnackbarDuration.Long
                    )
                }
            }
            is ProjectOverviewEvent.ArchiveError -> {
                coroutineScope.launch {
                    val errorMessage = getString(Res.string.error_archiving_projects)
                    snackbarHostState.showSnackbar(
                        message = errorMessage,
                        duration = SnackbarDuration.Long
                    )
                }
            }
            is ProjectOverviewEvent.PinError -> {
                coroutineScope.launch {
                    val errorMessage = getString(Res.string.error_pinning_projects)
                    snackbarHostState.showSnackbar(
                        message = errorMessage,
                        duration = SnackbarDuration.Long
                    )
                }
            }
            is ProjectOverviewEvent.NewProjectSaved -> {
                coroutineScope.launch {
                    val confirmMessage = getString(Res.string.project_saved_successfully)
                    snackbarHostState.showSnackbar(
                        message = confirmMessage,
                        duration = SnackbarDuration.Long
                    )
                }
                onNavigateToDetailScreen(event.projectId)
            }
            is ProjectOverviewEvent.AddToTrashError -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.error.asStringAsync(),
                        duration = SnackbarDuration.Long
                    )
                }
            }
            is ProjectOverviewEvent.ReorderError -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.error.asStringAsync(),
                        duration = SnackbarDuration.Long
                    )
                }
            }
            is ProjectOverviewEvent.OnLogoutError -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.error.asStringAsync()
                    )
                }
            }
            ProjectOverviewEvent.OnLogoutSuccess -> onSuccessfulLogout()
        }
    }

    BackHandler(enabled = state.isEditModeActive) {
        viewModel.onAction(ProjectOverviewAction.OnExitEditMode)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
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
            onNavigateToArchive = onNavigateToArchive,
            onNavigateToTrash = onNavigateToTrash,
            snackbarHostState = snackbarHostState,
            sortOption = sortOption
        )

        if (state.isLoading || state.isLoggingOut) {
            FullScreenLoadingIndicator()
        }
    }
}

@Composable
fun ProjectOverviewScreen(
    modifier: Modifier = Modifier,
    onAction: (ProjectOverviewAction) -> Unit,
    state: ProjectOverviewState,
    onNavigateToArchive: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
    sortOption: SortOption = SortOption.CUSTOM,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed)
) {

    val listState = rememberLazyListState()
    // Without this the bar collapses on any drag, even when the whole list fits on screen and
    // there is nothing to scroll to.
    val canScrollList = { listState.canScrollForward || listState.canScrollBackward }
    val enterAlwaysScrollBehavior =
        TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = canScrollList)
    val pinnedScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(canScroll = canScrollList)
    val scrollBehavior =
        if (state.isEditModeActive) pinnedScrollBehavior else enterAlwaysScrollBehavior
    val fabExpanded = listState.isScrollingUp()

    // If the content shrinks below one screen while the bar is collapsed, no scroll is left to
    // bring it back, so release it explicitly.
    LaunchedEffect(scrollBehavior) {
        snapshotFlow { listState.canScrollForward || listState.canScrollBackward }
            .collect { scrollable -> if (!scrollable) scrollBehavior.state.heightOffset = 0f }
    }
    val drawerScope = rememberCoroutineScope()
    val backState = rememberNavigationEventState(NavigationEventInfo.None)

    // Drag-to-reorder is only available under the Custom sort filter and while not searching.
    val reorderEnabled = sortOption == SortOption.CUSTOM && state.searchQuery.isBlank()
    // The displayed order lives in the ViewModel: a move updates it, a cancel or a failed write
    // re-derives it from what was actually persisted.
    val pinnedItems = state.pinnedProjects
    val otherItems = state.otherProjects
    val dragDropState = rememberProjectDragDropState(
        lazyListState = listState,
        onMove = { fromKey, toKey ->
            onAction(ProjectOverviewAction.OnReorderMove(fromId = fromKey, toId = toKey))
        }
    )

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
        onArchiveClick = onNavigateToArchive,
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
                    ProjectOverviewSelectionTopAppBar(
                        state = state,
                        onAction = onAction,
                        scrollBehavior = scrollBehavior
                    )
                } else {
                    ProjectOverviewSearchTopAppBar(
                        onAction = onAction,
                        state = state,
                        onMenuClick = { drawerScope.launch { drawerState.open() } },
                        username = state.localUser?.username,
                        email = state.localUser?.email,
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
                expanded = fabExpanded,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.new_project),
                        modifier = Modifier.size(26.dp)
                    )
                },
                text = {
                    Text(
                        text = stringResource(Res.string.new_project),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            )
        }

    ) { innerPadding ->
        // Applied per-child rather than to the Column, so the list can scroll its items
        // through the top bar / status bar band instead of being clipped below it.
        val topInset = innerPadding.calculateTopPadding()
        val bottomInset = innerPadding.calculateBottomPadding()
        // When the offline banner is shown it already sits under the bar and reserves the inset.
        val contentTopInset = if (state.isOnline) topInset else 0.dp
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            //Internet status
            if (!state.isOnline){
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondary)
                        .padding(top = topInset)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.no_internet_connection),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
            if (!state.isLoading && state.pinnedProjects.isEmpty() && state.otherProjects.isEmpty()){
                EmptySection(
                    title = stringResource(Res.string.no_current_projects),
                    description = stringResource(Res.string.no_current_projects_subtitle),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = contentTopInset, bottom = bottomInset)
                        .padding(horizontal = 8.dp)
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = {
                        onAction(ProjectOverviewAction.OnPullToRefresh)
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                            .testTag("project_overview"),
                        contentPadding = PaddingValues(
                            top = contentTopInset,
                            bottom = bottomInset
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (pinnedItems.isNotEmpty()) {
                            item {
                                ProjectSectionHeader(text = stringResource(Res.string.pinned))
                            }
                            items(
                                items = pinnedItems,
                                key = { it.projectId }
                            ) { item ->
                                ProjectListCard(
                                    item = item,
                                    state = state,
                                    onAction = onAction,
                                    reorderEnabled = reorderEnabled,
                                    dragDropState = dragDropState
                                )
                            }
                        }

                        if (otherItems.isNotEmpty()) {
                            item {
                                ProjectSectionHeader(
                                    text = if (state.searchQuery.isEmpty()) stringResource(Res.string.other) else stringResource(Res.string.search_results)
                                )
                            }
                        }

                        items(
                            items = otherItems,
                            key = { it.projectId }
                        ) { item ->
                            ProjectListCard(
                                item = item,
                                state = state,
                                onAction = onAction,
                                reorderEnabled = reorderEnabled,
                                dragDropState = dragDropState
                            )
                        }
                    }

                }
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
        if (state.showLogoutConfirmation) {
            AlertDialog(
                onDismissRequest = { onAction(ProjectOverviewAction.OnDismissLogoutConfirmation) },
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(Res.string.log_out),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                title = {
                    Text(
                        text = if (state.isOnline) stringResource(Res.string.do_you_want_to_logout)
                            else stringResource(Res.string.logout_not_possible)
                    )
                },
                text = {
                    Text(
                        text = if (state.isOnline) stringResource(Res.string.do_you_want_to_logout_desc)
                            else stringResource(Res.string.logout_not_possible_desc)
                    )
                },
                confirmButton = { if (state.isOnline)
                    TextButton(onClick = { onAction(ProjectOverviewAction.OnConfirmLogout) }) {
                        Text(text = stringResource(Res.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onAction(ProjectOverviewAction.OnDismissLogoutConfirmation) }) {
                        Text(
                            text = if (state.isOnline) stringResource(Res.string.cancel)
                                else stringResource(Res.string.close)
                        )
                    }
                }
            )
        }
    }
    }
}

/**
 * Returns `true` while the list is being scrolled toward the top (its beginning).
 * Canonical Google Compose-samples pattern: tracks the first visible item index and
 * offset across recompositions and derives the direction from their deltas.
 */
@Composable
private fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousScrollOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}

@Composable
private fun ProjectSectionHeader(text: String) {
    Text(
        modifier = Modifier
            .padding(bottom = 8.dp, top = 8.dp, start = 8.dp),
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LazyItemScope.ProjectListCard(
    item: ProjectUi,
    state: ProjectOverviewState,
    onAction: (ProjectOverviewAction) -> Unit,
    reorderEnabled: Boolean,
    dragDropState: ProjectDragDropState
) {
    // The dragged card (and the one settling back after release) drives its own translation and rides
    // above the rest; every other card animates to its new slot via animateItem().
    val isActive = item.projectId == dragDropState.draggingItemKey ||
        item.projectId == dragDropState.settlingItemKey
    val cardModifier = if (isActive) {
        Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationY = if (item.projectId == dragDropState.draggingItemKey) {
                    dragDropState.draggingItemOffset
                } else {
                    dragDropState.settlingItemOffset
                }
            }
    } else {
        Modifier.animateItem()
    }
    ProjectCard(
        modifier = cardModifier,
        projectUi = item,
        onClick = { onAction(ProjectOverviewAction.OnProjectCardClick(item.projectId)) },
        onLongClick = { onAction(ProjectOverviewAction.OnProjectCardLongPress(item.projectId)) },
        onToggleSelection = { onAction(ProjectOverviewAction.OnProjectCardToggleSelection(item.projectId)) },
        isEditModeActive = state.isEditModeActive,
        isSelected = item.projectId in state.selectedProjectIds,
        isReorderable = reorderEnabled,
        onReorderDragStart = {
            // Long-press enters edit mode (as before) and arms the drag.
            onAction(ProjectOverviewAction.OnProjectCardLongPress(item.projectId))
            dragDropState.onDragStart(item.projectId)
        },
        onReorderDrag = { dragAmountY ->
            // First movement turns the long-press into a reorder: leave edit mode.
            if (!dragDropState.hasMoved) onAction(ProjectOverviewAction.OnReorderDragStart)
            dragDropState.onDrag(dragAmountY)
        },
        onReorderDragEnd = {
            if (dragDropState.hasMoved) {
                // The ViewModel owns the order, so it reads the section this card sits in itself
                // rather than trusting a list captured back when this card last recomposed.
                onAction(ProjectOverviewAction.OnReorderCommit(item.projectId))
            }
            dragDropState.onDragEnd()
        },
        onReorderDragCancel = {
            // Aborted without a drop: discard the preview order before the card settles back.
            onAction(ProjectOverviewAction.OnReorderCancel)
            dragDropState.onDragCancel()
        }
    )
}

// The ViewModel fills the section lists in production; previews build the state by hand, so they do
// the same split here.
private fun ProjectOverviewState.withPreviewSections(): ProjectOverviewState {
    val visible = projects.orEmpty()
        .filter { searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) }
    return copy(
        pinnedProjects = visible.filter { it.isPinned },
        otherProjects = visible.filterNot { it.isPinned }
    )
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
                projectTaskId = "t1",
                title = "Task 1",
                description = null,
                formattedDuration = "2h 30m",
                formattedStateDateTime = "10:00",
                formattedEndDateTimeUtc = "",
                isTimerRunning = true,
                subTasks = emptyList(),
                isFinished = false
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
        isPinned = true,
        projectTasks = listOf(
            ProjectTaskUi(
                projectTaskId = "t2",
                title = "Task 2",
                description = null,
                formattedDuration = "5h 15m",
                formattedStateDateTime = "09:00",
                formattedEndDateTimeUtc = "17:00",
                isTimerRunning = false,
                subTasks = emptyList(),
                isFinished = false
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
                localUser = User(id = "123", username = "JoergVoye", email = "joerg@example.com", hasVerifiedEmail = true ),
                isOnline = false
            ).withPreviewSections(),
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
                searchQuery = "Run",
                localUser = User(id = "123", username = "JoergVoye", email = "joerg@example.com", hasVerifiedEmail = true )
            ).withPreviewSections(),
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
                isEditModeActive = true,
                selectedProjectIds = setOf("1", "3"),
                localUser = User(id = "123", username = "JoergVoye", email = "joerg@example.com", hasVerifiedEmail = true )
            ).withPreviewSections(),
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
                localUser = User(id = "123", username = "JoergVoye", email = "joerg@example.com", hasVerifiedEmail = true )
            ).withPreviewSections(),
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
                    localUser = User(id = "123", username = "JoergVoye", email = "joerg@example.com", hasVerifiedEmail = true )
                ).withPreviewSections(),
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