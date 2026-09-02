package com.jvcs.tracky.features.project.presentation.project_detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import com.jvcs.tracky.design_system.components.DurationHeroCard
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import com.jvcs.tracky.design_system.util.rememberCollapsibleScrollBehavior
import com.jvcs.tracky.features.project.presentation.models.ProjectSubTaskUi
import com.jvcs.tracky.features.project.presentation.project_detail.components.AddNewProjectTaskBottomSheet
import com.jvcs.tracky.features.project.presentation.project_detail.components.ColorInfoCard
import com.jvcs.tracky.features.project.presentation.project_detail.components.InfoCard
import com.jvcs.tracky.features.project.presentation.project_detail.components.TaskItemCard
import com.jvcs.tracky.features.project.presentation.project_detail.components.TrackyColorPicker
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.description
import tracky.composeapp.generated.resources.last_active
import tracky.composeapp.generated.resources.ok
import tracky.composeapp.generated.resources.light_text_color
import tracky.composeapp.generated.resources.project_duration
import tracky.composeapp.generated.resources.select_project_color
import tracky.composeapp.generated.resources.start_date
import tracky.composeapp.generated.resources.task_completed_count
import tracky.composeapp.generated.resources.tasks
import tracky.composeapp.generated.resources.tasks_completed
import tracky.composeapp.generated.resources.title
import tracky.composeapp.generated.resources.uncheck_task_blocked_message
import tracky.composeapp.generated.resources.uncheck_task_blocked_title

@Composable
fun ProjectDetailScreenRoot(
    navigateBack: () -> Unit,
    onEditTextClick: (isEditMode: Boolean, title: String, description: String, colorArgb: Int?) -> Unit,
    onProjectTaskClick: (String) -> Unit,
    viewModel: ProjectDetailViewModel = koinViewModel ()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when(event) {
            is ProjectDetailEvent.Error -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.error.toString(),
                        duration = SnackbarDuration.Short
                    )
                }
            }
            is ProjectDetailEvent.NewProjectSessionSaved -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Task saved successfully!",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    ProjectDetailScreen(
        state = state,
        onAction = { action ->
            when(action) {
                ProjectDetailAction.OnBackClick -> navigateBack()
                is ProjectDetailAction.OnEditTextClick ->
                    onEditTextClick(
                        state.isEditMode,
                        action.title,
                        action.description,
                        state.projectColor?.toArgb()
                    )
                is ProjectDetailAction.OnProjectSessionCardClick -> onProjectTaskClick(action.projectSessionId)
                else -> Unit
            }
            viewModel.onAction(action)
        },
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    state: ProjectDetailState,
    onAction: (ProjectDetailAction) -> Unit,
    snackbarHostState: SnackbarHostState
) {

    val listState = rememberLazyListState()
    // One instance, shared by the app bar and the nested-scroll connection below. Calling the
    // helper again at either site would orphan a behavior and freeze the list.
    val scrollBehavior = rememberCollapsibleScrollBehavior(
        listState = listState,
        pinned = state.isEditMode
    )

    // Composited against the Scaffold background so it is fully opaque: the top bar uses this
    // colour too, and list items scrolling underneath must not show through it.
    val headerColor = state.projectColor?.copy(alpha = 0.12f)
        ?.compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
        ?: MaterialTheme.colorScheme.onSurface
    // The header paints edge-to-edge behind the status bar, so it has to reserve the space
    // the bar and the status bar occupy itself. Both values are constant, unlike the
    // Scaffold's top padding, which shrinks frame by frame as the bar collapses.
    val headerTopInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() +
            TopAppBarDefaults.TopAppBarExpandedHeight

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (state.isEditMode) "EDIT PROJECT" else "PROJECT DETAILS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isEditMode) onAction(ProjectDetailAction.OnCloseAndCancelClick)
                        else onAction(ProjectDetailAction.OnBackClick)
                    }) {
                        Icon(
                            if (state.isEditMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (state.isEditMode) "Cancel" else "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (state.isEditMode) onAction(ProjectDetailAction.OnSaveClick)
                        else onAction(ProjectDetailAction.OnEditModeClick)
                    }) {
                        Icon(
                            if (state.isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "Action"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerColor,
                    scrolledContainerColor = headerColor
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        if (state.project == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Header
                item {
                    Column(
                        modifier = Modifier
                            .background(
                                color = headerColor,
                                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                            )
                            .padding(top = headerTopInset),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProjectHeader(
                            modifier = Modifier
                                .padding(horizontal = 16.dp),
                            title = state.titleText ?: stringResource(Res.string.title),
                            description = state.descriptionText ?: stringResource(Res.string.description),
                            onAction = onAction,
                            state = state
                        )
                        if (state.isEditMode) {
                            ColorInfoCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                label = stringResource(Res.string.select_project_color),
                                colorValue = state.projectColor ?: Color.Cyan,
                                hexCode = state.selectedColorHex,
                                isEditMode = state.isEditMode,
                                onClick = { onAction(ProjectDetailAction.OnToggleColorPicker) }
                            )
                            TextColorToggle(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp),
                                useLightTextColor = state.useLightTextColor,
                                onToggle = { onAction(ProjectDetailAction.OnUseLightTextColorToggled(it)) }
                            )
                        }
                        DurationHeroCard(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            label = stringResource(Res.string.project_duration),
                            totalDuration = state.project.totalProjectDuration ?: "00:00:00:00",
                            projectColor = state.projectColor ?: MaterialTheme.colorScheme.onSurface,
                            useLightTextColor = state.useLightTextColor,
                            onStartStopClick = {
                                // Logic for project-wide tracker if needed
                                onAction(ProjectDetailAction.OnStartTrackerClick)
                            }
                        )
                    }
                }

                // 2. Info Grid
                item {
                    InfoGrid(
                        modifier = Modifier
                            .padding(vertical = 16.dp),
                        startDate = state.project.startDateTimeUtc,
                        lastActive = state.project.startDateTimeUtc,
                        state = state
                    )
                }

                // 4. Sessions Header
                item {
                    TasksHeader(
                        modifier = Modifier
                            .padding(horizontal = 16.dp),
                        onAddClick = {
                            onAction(ProjectDetailAction.OnToggleAddNewProjectSessionBottomSheet)
                        },
                        addButtonContainerColor = state.projectColor ?: MaterialTheme.colorScheme.primary,
                        addButtonContentColor = if (state.useLightTextColor) Color.White else Color.Black
                    )
                }

                // 5. Session Items
                itemsIndexed(state.project.projectTasks ?: emptyList()) { index, session ->
                    TaskItemCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp),
                        index = index + 1,
                        task = session,
                        projectColor = state.projectColor ?: MaterialTheme.colorScheme.primary,
                        isEditMode = state.isEditMode,
                        onToggleTimer = {
                            onAction(ProjectDetailAction.OnToggleSessionTimer(session.projectTaskId))
                        },
                        onDeleteClick = {
                            onAction(ProjectDetailAction.OnDeleteSessionClick(session.projectTaskId))
                        },
                        onCardClick = {
                            onAction(ProjectDetailAction.OnProjectSessionCardClick(session.projectTaskId))
                        },
                        onCheckedChange = {
                            onAction(ProjectDetailAction.OnTaskCheckedChange(session.projectTaskId))
                        },
                        onToggleSubTaskTimer = { subTaskId ->
                            onAction(ProjectDetailAction.OnToggleSubTaskTimer(subTaskId))
                        },
                        onDeleteSubTaskClick = { subTaskId ->
                            onAction(ProjectDetailAction.OnDeleteSubTaskClick(subTaskId))
                        },
                        onSubTaskCheckedChange = { subTaskId ->
                            onAction(ProjectDetailAction.OnSubTaskCheckedChange(subTaskId))
                        },
                        isExpanded = session.projectTaskId !in state.collapsedTaskIds,
                        onToggleExpanded = {
                            onAction(ProjectDetailAction.OnToggleTaskExpanded(session.projectTaskId))
                        },
                        editingSubTaskId = state.editingSubTaskId,
                        editSubTaskTextFieldState = state.editSubTaskTextFieldState,
                        isAddingSubTask = state.pendingSubTaskParentTaskId == session.projectTaskId,
                        onAddSubTaskClick = {
                            onAction(ProjectDetailAction.OnAddSubTaskClick(session.projectTaskId))
                        },
                        onSubTaskTitleClick = { subTaskId, currentTitle ->
                            onAction(ProjectDetailAction.OnSubTaskTitleClick(subTaskId, currentTitle))
                        },
                        onCommitSubTaskTitle = {
                            onAction(ProjectDetailAction.OnCommitSubTaskTitle)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
    if (state.isAddNewProjectTaskBottomSheetVisible) {
        AddNewProjectTaskBottomSheet(
            state = state,
            onAction = onAction
        )
    }
    if (state.isColorPickerVisible) {
        TrackyColorPicker(
            currentColor = state.projectColor ?: Color.Cyan,
            onCancel = { onAction(ProjectDetailAction.OnToggleColorPicker) },
            onSave = { onAction(ProjectDetailAction.OnColorChanged(it)) }
        )
    }
    // Purely informational — there is nothing to confirm, so it gets one OK and no dismiss button.
    if (state.isUncheckTaskBlockedDialogVisible) {
        AlertDialog(
            onDismissRequest = { onAction(ProjectDetailAction.OnDismissUncheckTaskDialog) },
            title = { Text(text = stringResource(Res.string.uncheck_task_blocked_title)) },
            text = { Text(text = stringResource(Res.string.uncheck_task_blocked_message)) },
            confirmButton = {
                TextButton(onClick = { onAction(ProjectDetailAction.OnDismissUncheckTaskDialog) }) {
                    Text(text = stringResource(Res.string.ok))
                }
            }
        )
    }
}


@Composable
private fun ProjectHeader(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    onAction: (ProjectDetailAction) -> Unit,
    state: ProjectDetailState,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (state.isEditMode) MaterialTheme.colorScheme.surfaceContainerLow
                else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if(state.isEditMode) MaterialTheme.colorScheme.outlineVariant
                        else Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable {
                onAction(
                    ProjectDetailAction.OnEditTextClick(
                        title = state.titleText.orEmpty(),
                        description = state.descriptionText.orEmpty()
                    )
                )
            },
        ) {
        Text(
            modifier = Modifier
                .padding(16.dp),
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            text = description,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

    }

}

@Composable
private fun InfoGrid(
    modifier: Modifier = Modifier,
    startDate: String,
    lastActive: String,
    state: ProjectDetailState,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(Modifier.weight(1f), Icons.Outlined.DateRange, stringResource(Res.string.start_date), startDate)
            InfoCard(Modifier.weight(1f), Icons.Outlined.History, stringResource(Res.string.last_active), lastActive)
        }

        InfoCard(
            modifier = Modifier
                .fillMaxWidth(),
            icon = Icons.Outlined.GridView,
            label = stringResource(Res.string.tasks_completed),
            value = stringResource(
                Res.string.task_completed_count,
                state.project?.doneTaskCount ?: 0,
                state.project?.projectTasks?.size ?: 0
            )
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                // Lambda overload: the progress is read in the draw phase, so animating it
                // later will not recompose the card.
                progress = { state.project?.taskProgress ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = state.projectColor ?: MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
    }
}

@Composable
private fun TextColorToggle(
    modifier: Modifier = Modifier,
    useLightTextColor: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.light_text_color),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = useLightTextColor,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                )
            )
        }
    }
}

@Composable
private fun TasksHeader(
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit,
    addButtonContainerColor: Color,
    addButtonContentColor: Color
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = stringResource(Res.string.tasks), style = MaterialTheme.typography.headlineMedium)
        FilledIconButton(
            onClick = onAddClick,
            shape = RoundedCornerShape(16.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor =  addButtonContainerColor,
                contentColor = addButtonContentColor
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Task")
        }
    }
}



@Preview(device = Devices.PIXEL_9_PRO)
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
                    projectTasks = projectSessionsPreview
                ),
                isEditMode = false
            ),
            snackbarHostState = SnackbarHostState()
        )
    }
}

@Preview(device = Devices.PIXEL_9_PRO)
@Composable
private fun ProjectDetailScreenEditModePreview() {
    TrackyTheme {
        ProjectDetailScreen(
            onAction = {},
            state = ProjectDetailState(
                project = ProjectUi(
                    projectId = "1",
                    title = "Project One",
                    description = "Description 1",
                    color = Color.Yellow,
                    totalDuration = "10:00:00",
                    startDateTimeUtc = "2023-01-01T00:00:00Z",
                    isFinished = false,
                    endDateTimeUtc = null,
                    projectTasks = projectSessionsPreview
                ),
                isEditMode = true
            ),
            snackbarHostState = SnackbarHostState()
        )
    }
}

private val projectSessionsPreview = listOf(
    ProjectTaskUi(
        projectTaskId = "1",
        title = "This is session One",
        description = "Description 1",
        formattedDuration = "10:00:00",
        formattedStateDateTime = "2023-01-01T00:00:00Z",
        formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
        isTimerRunning = false,
        subTasks = emptyList(),
        isFinished = false
    ),
    ProjectTaskUi(
        projectTaskId = "2",
        title = "This is session Two",
        description = "Description 2",
        formattedDuration = "10:00:00",
        formattedStateDateTime = "2023-01-01T00:00:00Z",
        formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
        isTimerRunning = false,
        subTasks = listOf(
            ProjectSubTaskUi(
                projectSubTaskId = "1",
                title = "SubTask 1",
                description = "Description SubTask 1",
                formattedDuration = "05:35:53",
                formattedStartDateTime = "23.08.2026, 10:15",
                formattedEndDateTimeUtc = null,
                isTimerRunning = false,
                isFinished = false
            ),
            ProjectSubTaskUi(
                projectSubTaskId = "2",
                title = "SubTask 2",
                description = "Description SubTask 2",
                formattedDuration = "05:35:53",
                formattedStartDateTime = "23.08.2026, 13:15",
                formattedEndDateTimeUtc = null,
                isTimerRunning = true,
                isFinished = false
            ),
            ProjectSubTaskUi(
                projectSubTaskId = "3",
                title = "SubTask 3",
                description = "Description SubTask 3",
                formattedDuration = "05:35:53",
                formattedStartDateTime = "23.08.2026, 16:30",
                formattedEndDateTimeUtc = "23.08.2026, 17:00",
                isTimerRunning = false,
                isFinished = true
            )
        ),
        isFinished = false
    ),
    ProjectTaskUi(
        projectTaskId = "3",
        title = "This is session Three",
        description = null,
        formattedDuration = "10:00:00",
        formattedStateDateTime = "2023-01-01T00:00:00Z",
        formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
        isTimerRunning = false,
        subTasks = emptyList(),
        isFinished = false
    )
)