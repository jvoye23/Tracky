package com.jvcs.tracky.features.project_tracker.presentation.project_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.core.presentation.model.ProjectSessionUi
import com.jvcs.tracky.core.presentation.model.ProjectUi
import com.jvcs.tracky.design_system.Icon_ChevronRight
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.theme.timerStyle
import com.jvcs.tracky.features.project_tracker.domain.EditTextType
import com.jvcs.tracky.features.project_tracker.presentation.project_detail.components.AddNewProjectSessionBottomSheet
import com.jvcs.tracky.features.project_tracker.presentation.project_detail.components.ProjectDetailTopAppBar
import com.jvcs.tracky.features.project_tracker.presentation.project_detail.components.ProjectSessionCard
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.description
import tracky.composeapp.generated.resources.sessions
import tracky.composeapp.generated.resources.title
import tracky.composeapp.generated.resources.total_duration


@Composable
fun ProjectDetailScreenRoot(
    navigateBack: () -> Unit,
    onEditTextClick: (String?, EditTextType) -> Unit,
    viewModel: ProjectDetailViewModel = koinViewModel ()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProjectDetailScreen(
        state = state,
        onAction = { action ->
            when(action) {
                ProjectDetailAction.OnCloseAndCancelClick -> navigateBack()
                is ProjectDetailAction.OnEditTextClick -> onEditTextClick(action.text, action.editTextType)
                else -> Unit
            }
            viewModel.onAction(action)
        }
    )

}


@Composable
fun ProjectDetailScreen(
    onAction: (ProjectDetailAction) -> Unit,
    state: ProjectDetailState
) {

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        contentColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            ProjectDetailTopAppBar(
                isEditMode = state.isEditMode,
                onAction = onAction,
                project = state.project
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.padding(16.dp),
                text = { Text(text = "To Do later") },
                icon = { Icon(Icons.Outlined.Timer, contentDescription = "Edit") },
                onClick = { onAction(ProjectDetailAction.OnStartTrackerClick) },
                expanded = state.isFabExtended, // Collapses when you scroll down
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )

        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.errorMessage != null){
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.error),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.errorMessage,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }


            item {
                TitleSection(
                    onAction = onAction,
                    state = state
                )
            }
            item {
                DescriptionSection(
                    onAction = onAction,
                    state = state
                )
            }
            item {
                TotalDurationSection(
                    onAction = onAction,
                    state = state
                )
            }

            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    TimerSection(
                        modifier = Modifier
                            .padding(16.dp),
                        onAction = onAction,
                        state = state
                    )

                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (state.isEditMode) onAction(
                                ProjectDetailAction.OnEditTextClick(
                                    text = state.titleText,
                                    editTextType = EditTextType.TITLE

                                )
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f),
                        text = stringResource(Res.string.sessions),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        modifier = Modifier,
                        onClick = {
                            onAction(ProjectDetailAction.OnToggleAddNewProjectSessionBottomSheet)
                        },
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(20.dp),
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )

                    }
                }
            }

            items(
                items = state.project?.projectSessions ?: emptyList(),
                key = { it.id ?: "" }
            ) {
                ProjectSessionCard(
                    projectSessionUi = it,
                    onAction = onAction,
                    state = state
                )
            }
        }
    }
    if (state.isAddNewProjectSessionBottomSheetVisible) {
        AddNewProjectSessionBottomSheet(
            state = state,
            onAction = onAction
        )
    }

}

@Composable
private fun TitleSection (
    modifier: Modifier = Modifier,
    onAction: (ProjectDetailAction) -> Unit,
    state: ProjectDetailState,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (state.isEditMode) onAction(
                    ProjectDetailAction.OnEditTextClick(
                        text = state.titleText,
                        editTextType = EditTextType.TITLE

                    )
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {

        Text(
            modifier = Modifier
                .weight(1f),
            text = state.titleText ?: stringResource(Res.string.title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            modifier = Modifier
                .size(20.dp),
            imageVector = Icon_ChevronRight,
            contentDescription = null,
            tint = if (state.isEditMode) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
        )
    }
}

@Composable
private fun DescriptionSection (
    modifier: Modifier = Modifier,
    onAction: (ProjectDetailAction) -> Unit,
    state: ProjectDetailState,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (state.isEditMode) onAction(
                    ProjectDetailAction.OnEditTextClick(
                        text = state.descriptionText,
                        editTextType = EditTextType.DESCRIPTION
                    )
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            modifier = Modifier
                .weight(1f),
            text = state.descriptionText ?: stringResource(Res.string.description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            modifier = Modifier
                .size(20.dp),
            imageVector = Icon_ChevronRight,
            contentDescription = null,
            tint = if (state.isEditMode) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
        )
    }
}

@Composable
private fun TotalDurationSection(
    modifier: Modifier = Modifier,
    onAction: (ProjectDetailAction) -> Unit,
    state: ProjectDetailState
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            modifier = Modifier
                ,
            text = stringResource(Res.string.total_duration),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            modifier = Modifier,
            text = state.project?.totalDuration ?: "00:00:00:00",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

    }

}

@Composable
private fun TimerSection(
    modifier: Modifier = Modifier,
    onAction: (ProjectDetailAction) -> Unit,
    state: ProjectDetailState,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Current Session Recording",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        //Spacer(modifier = Modifier.height(8.dp))
        /*Button(
            onClick = {
               onAction(ProjectDetailAction.OnStartTrackerClick)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)

        ) {
            Text(
                text = if (state.isTimerRunning) "Stop timer" else "Start timer"
            )
        }*/
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = state.project?.totalProjectDuration ?: "00:00:00:00",
            style = MaterialTheme.typography.timerStyle,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )


    }

}


@Preview
@Composable
private fun ProjectDetailScreenPreview() {
    TrackyTheme {
        ProjectDetailScreen(
            onAction = {},
            state = ProjectDetailState(
                project = ProjectUi(
                    projectId = "1",
                    title = "Project One",
                    description = "Description 1",
                    color = Color.Red,
                    totalDuration = "10:00:00",
                    startDateTimeUtc = "2023-01-01T00:00:00Z",
                    isFinished = false,
                    endDateTimeUtc = null,
                    projectSessions = projectSessions
                )
            )
        )
    }
}

private val projectSessions = listOf(
    ProjectSessionUi(
        id = "1",
        title = "This is session One",
        formattedDuration = "10:00:00",
        formattedStateDateTime = "2023-01-01T00:00:00Z",
        formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
        isTimerRunning = false
    ),
    ProjectSessionUi(
        id = "2",
        title = "This is session Two",
        formattedDuration = "10:00:00",
        formattedStateDateTime = "2023-01-01T00:00:00Z",
        formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
        isTimerRunning = false

    ),
    ProjectSessionUi(
        id = "3",
        title = "This is session Three",
        formattedDuration = "10:00:00",
        formattedStateDateTime = "2023-01-01T00:00:00Z",
        formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
        isTimerRunning = false
    ),
    ProjectSessionUi(
        id = "4",
        title = "This is session Four",
        formattedDuration = "10:00:00",
        formattedStateDateTime = "2023-01-01T00:00:00Z",
        formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
        isTimerRunning = false
    ),
    ProjectSessionUi(
        id = "5",
        title = "This is session Five",
        formattedDuration = "10:00:00",
        formattedStateDateTime = "2023-01-01T00:00:00Z",
        formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
        isTimerRunning = false
    ),
    ProjectSessionUi(
        id = "6",
        title = "This is session Six",
        formattedDuration = "10:00:00",
        formattedStateDateTime = "2023-01-01T00:00:00Z",
        formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
        isTimerRunning = false
    )
)