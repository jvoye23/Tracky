package com.jvcs.tracky.features.project.presentation.project_detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import com.jvcs.tracky.design_system.Icon_ChevronRight
import com.jvcs.tracky.design_system.components.DurationHeroCard
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import com.jvcs.tracky.features.project.domain.project.EditTextType
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
import tracky.composeapp.generated.resources.light_text_color
import tracky.composeapp.generated.resources.project_color
import tracky.composeapp.generated.resources.project_duration
import tracky.composeapp.generated.resources.start_date
import tracky.composeapp.generated.resources.tasks
import tracky.composeapp.generated.resources.title

@Composable
fun ProjectDetailScreenRoot(
    navigateBack: () -> Unit,
    onEditTextClick: (String?, EditTextType) -> Unit,
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

    NewProjectDetailScreen(
        state = state,
        onAction = { action ->
            when(action) {
                ProjectDetailAction.OnBackClick -> navigateBack()
                is ProjectDetailAction.OnEditTextClick -> onEditTextClick(action.text, action.editTextType)
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
fun NewProjectDetailScreen(
    state: ProjectDetailState,
    onAction: (ProjectDetailAction) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val project = state.project

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (state.isEditMode) "EDIT PROJECT" else "PROJECT DETAILS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
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
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) { paddingValues ->
        if (project == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Header
                item {
                    ProjectHeader(
                        title = state.titleText ?: stringResource(Res.string.title),
                        description = state.descriptionText ?: stringResource(Res.string.description),
                        onAction = onAction,
                        state = state
                    )
                }

                // 2. Info Grid
                item {
                    InfoGrid(
                        modifier = Modifier
                            .padding(vertical = 16.dp),
                        startDate = state.project.startDateTimeUtc,
                        lastActive = state.project.startDateTimeUtc,
                        sessionCount = state.project.projectTasks?.size ?: 0,
                        color = state.selectedColor ?: Color.Cyan,
                        state = state,
                        onAction = onAction
                    )
                }

                // 3. Project Duration Hero Card
                item {
                    DurationHeroCard(
                        modifier = Modifier
                            .padding(bottom = 16.dp),
                        label = stringResource(Res.string.project_duration),
                        totalDuration = state.project.totalProjectDuration ?: "00:00:00:00",
                        projectColor = state.selectedColor ?: MaterialTheme.colorScheme.primary,
                        useLightTextColor = state.useLightTextColor,
                        onStartStopClick = {
                            // Logic for project-wide tracker if needed
                            onAction(ProjectDetailAction.OnStartTrackerClick)
                        }
                    )
                }

                if (state.isEditMode) {
                    item {
                        TextColorToggle(
                            useLightTextColor = state.useLightTextColor,
                            onToggle = { onAction(ProjectDetailAction.OnUseLightTextColorToggled(it)) }
                        )
                    }
                }

                // 4. Sessions Header
                item {
                    TasksHeader(onAddClick = {
                        onAction(ProjectDetailAction.OnToggleAddNewProjectSessionBottomSheet)
                    })
                }

                // 5. Session Items
                itemsIndexed(project.projectTasks ?: emptyList()) { index, session ->
                    TaskItemCard(
                        index = index + 1,
                        task = session,
                        projectColor = state.selectedColor ?: MaterialTheme.colorScheme.primary,
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
                        onCheckedChange = {},
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
            currentColor = state.selectedColor ?: Color.Cyan,
            onCancel = { onAction(ProjectDetailAction.OnToggleColorPicker) },
            onSave = { onAction(ProjectDetailAction.OnColorChanged(it)) }
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
    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(if (state.isEditMode) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent)
                .padding(vertical = 4.dp)
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
                text = title,
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
        //Description
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(if (state.isEditMode) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent)
                .padding(vertical = 4.dp)
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
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun InfoGrid(
    modifier: Modifier = Modifier,
    startDate: String,
    lastActive: String,
    sessionCount: Int,
    color: Color,
    state: ProjectDetailState,
    onAction: (ProjectDetailAction) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(Modifier.weight(1f), Icons.Outlined.DateRange, stringResource(Res.string.start_date), startDate)
            InfoCard(Modifier.weight(1f), Icons.Outlined.History, stringResource(Res.string.last_active), lastActive)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(Modifier.weight(1f), Icons.Outlined.GridView, stringResource(Res.string.tasks), "$sessionCount Total")
            ColorInfoCard(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.project_color),
                colorValue = color,
                hexCode = state.selectedColorHex,
                isEditMode = state.isEditMode,
                onClick = { onAction(ProjectDetailAction.OnToggleColorPicker) }
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
private fun TasksHeader(onAddClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = stringResource(Res.string.tasks), style = MaterialTheme.typography.headlineMedium)
        FilledIconButton(
            onClick = onAddClick,
            shape = RoundedCornerShape(16.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Task")
        }
    }
}



@Preview(device = Devices.PIXEL_9_PRO)
@Composable
private fun NewProjectDetailScreenPreview() {
    TrackyTheme {
        NewProjectDetailScreen(
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