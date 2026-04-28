package com.jvcs.tracky.features.project_tracker.presentation.project_overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.design_system.components.UserProfileButton
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.AddNewProjectBottomSheet
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.components.ProjectCard
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.project_overview

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


    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onAction(
                ProjectOverviewAction.OnFabClick
            )

            }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add"
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End

    ) { innerPadding ->
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
            onLogout = onLogout,
            modifier = Modifier
                .padding(innerPadding)
        )

    }

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("project_overview")

    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 16.dp)
                    .background(
                        shape = RoundedCornerShape(50.dp),
                        color = MaterialTheme.colorScheme.primary .copy(alpha = 0.08f)
                    )
                    .size(32.dp),

                onClick = { onAction(ProjectOverviewAction.OnCalendarIconClick) }
            ) {
                Icon(
                    modifier = Modifier
                        ,
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                modifier = Modifier,
                text = stringResource(Res.string.project_overview),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (username != null && email != null) {
                UserProfileButton(
                    username = username,
                    email = email,
                    onLogoutClick = onLogout,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(horizontal = 16.dp)
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    modifier = Modifier
                        .padding(all = 16.dp),
                    text = "My current open projects"
                )
            }

            items(
                items = state.projects ?: emptyList(),
                key = { it.projectId!! }
            ) { item ->
                ProjectCard(
                    projectUi = item,
                    onAction = onAction,
                    state = state
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