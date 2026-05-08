@file:OptIn(ExperimentalMaterial3Api::class)

package com.jvcs.tracky.features.project_tracker.presentation.project_overview

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.AddNewProjectBottomSheet
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.ProjectCard
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.ProjectOverViewTopBar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.current_projects
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

    // TODO: Handle context for Toast
    //val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when(event) {
            is ProjectOverviewEvent.Error -> {
                /* Toast.makeText(
                    context,
                    event.error.asString(context),
                    Toast.LENGTH_LONG
                ).show()*/
            }
            is ProjectOverviewEvent.NewProjectSaved -> {
                /*Toast.makeText(
                    context,
                    R.string.tasky_item_saved,
                    Toast.LENGTH_LONG
                ).show()*/
                onNavigateToDetailScreen(event.projectId)
            }

        }
    }
    ProjectOverviewScreen(
        onAction = { action ->
            when(action) {
                is ProjectOverviewAction.OnProjectCardClick -> onNavigateToDetailScreen(action.projectId)
                else -> Unit
            }
            viewModel.onAction(action)
        },
        state = state,
        username = username,
        email = email,
        onLogout = onLogout
    )
}

@Composable
fun ProjectOverviewScreen(
    onAction: (ProjectOverviewAction) -> Unit,
    state: ProjectOverviewState,
    username: String?,
    email: String?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ProjectOverViewTopBar(
                modifier = Modifier.padding(horizontal = 10.dp),
                onAction = onAction,
                state = state,
                onLogout = onLogout,
                username = username,
                email = email,
                scrollBehavior = scrollBehavior
            )
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
        onLogout = {}
    )
}