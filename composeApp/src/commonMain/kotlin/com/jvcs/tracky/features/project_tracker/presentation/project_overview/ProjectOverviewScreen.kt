@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.jvcs.tracky.features.project_tracker.presentation.project_overview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.design_system.Icon_Delete
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.AddNewProjectBottomSheet
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.ProjectCard
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.ProjectOverViewTopBar
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.ProjectOverviewEditModeTopBar
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
import tracky.composeapp.generated.resources.search_results

@Composable
fun ProjectOverviewScreenRoot(
    modifier: Modifier = Modifier,
    username: String?,
    email: String?,
    onLogout: () -> Unit,
    onNavigateToDetailScreen: (String) -> Unit,
    viewModel: ProjectOverviewViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            is ProjectOverviewEvent.NewProjectSaved -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Project saved successfully!",
                        duration = SnackbarDuration.Short
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
        snackbarHostState = snackbarHostState
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
    snackbarHostState: SnackbarHostState
) {

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

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
                key = { it.projectId!! }
            ) { item ->
                ProjectCard(
                    projectUi = item,
                    isEditModeActive = state.isEditModeActive,
                    isSelected = item.projectId != null && item.projectId in state.selectedProjectIds,
                    onAction = onAction
                )

            }

        }
        if (state.isAddNewProjectBottomSheetVisible) {
            AddNewProjectBottomSheet(
                state = state,
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

@Preview
@Composable
fun ProjectOverviewScreenPreview() {
    ProjectOverviewScreen(
        onAction = {},
        state = ProjectOverviewState(),
        username = "JoergVoye",
        email = "joerg@example.com",
        onLogout = {},
        snackbarHostState = SnackbarHostState()
    )
}