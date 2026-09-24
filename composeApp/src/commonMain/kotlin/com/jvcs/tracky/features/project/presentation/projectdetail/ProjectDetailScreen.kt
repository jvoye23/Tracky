package com.jvcs.tracky.features.project.presentation.projectdetail

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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CalendarMonth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.designsystem.components.DurationHeroCard
import com.jvcs.tracky.designsystem.components.InfoCard
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.util.ObserveAsEvents
import com.jvcs.tracky.designsystem.util.rememberCollapsibleScrollBehavior
import com.jvcs.tracky.features.project.presentation.edittext.EditTextTarget
import com.jvcs.tracky.features.project.presentation.models.PerDayStripUi
import com.jvcs.tracky.features.project.presentation.models.ProjectSubTaskUi
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import com.jvcs.tracky.features.project.presentation.projectdetail.components.AddNewProjectTaskBottomSheet
import com.jvcs.tracky.features.project.presentation.projectdetail.components.ColorInfoCard
import com.jvcs.tracky.features.project.presentation.projectdetail.components.PerDayCard
import com.jvcs.tracky.features.project.presentation.projectdetail.components.TaskItemCard
import com.jvcs.tracky.features.project.presentation.projectdetail.components.TrackyColorPicker
import com.jvcs.tracky.features.project.presentation.util.rememberReorderableListState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.add_task
import tracky.composeapp.generated.resources.daily_overview_title
import tracky.composeapp.generated.resources.description
import tracky.composeapp.generated.resources.edit
import tracky.composeapp.generated.resources.last_active
import tracky.composeapp.generated.resources.light_text_color
import tracky.composeapp.generated.resources.ok
import tracky.composeapp.generated.resources.project_duration
import tracky.composeapp.generated.resources.save
import tracky.composeapp.generated.resources.select_project_color
import tracky.composeapp.generated.resources.start_date
import tracky.composeapp.generated.resources.task_completed_count
import tracky.composeapp.generated.resources.tasks
import tracky.composeapp.generated.resources.tasks_completed
import tracky.composeapp.generated.resources.timer_running_on_another_device
import tracky.composeapp.generated.resources.timer_stale_on_another_device
import tracky.composeapp.generated.resources.title
import tracky.composeapp.generated.resources.uncheck_task_blocked_message
import tracky.composeapp.generated.resources.uncheck_task_blocked_title

/** Sentinel for "open on today", matching the route's default. */
private const val OPEN_ON_TODAY = -1L

@Composable
fun ProjectDetailScreenRoot(
    navigateBack: () -> Unit,
    onEditTextClick: (
        isEditMode: Boolean,
        projectId: String,
        target: EditTextTarget,
        taskId: String?,
        subTaskId: String?,
    ) -> Unit,
    onProjectTaskClick: (String) -> Unit,
    onDailyOverviewClick: (epochDay: Long) -> Unit,
    viewModel: ProjectDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ProjectDetailEvent.Error -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.error.toString(),
                        duration = SnackbarDuration.Short,
                    )
                }
            }

            is ProjectDetailEvent.ReorderError -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.error.toString(),
                        duration = SnackbarDuration.Short,
                    )
                }
            }

            is ProjectDetailEvent.NewProjectSessionSaved -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Task saved successfully!",
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }

    ProjectDetailScreen(
        state = state,
        onAction = { action ->
            when (action) {
                ProjectDetailAction.OnBackClick -> {
                    navigateBack()
                }

                is ProjectDetailAction.OnDailyOverviewClick -> {
                    onDailyOverviewClick(action.epochDay)
                }

                is ProjectDetailAction.OnProjectEditTextClick -> {
                    onEditTextClick(
                        action.isEditMode,
                        action.projectId,
                        EditTextTarget.PROJECT,
                        null,
                        null,
                    )
                }

                // The three task-level taps below only exist in edit mode, so the editor opens
                // straight into editing.
                is ProjectDetailAction.OnTaskTitleClick -> {
                    state.project?.let {
                        onEditTextClick(true, it.projectId, EditTextTarget.TASK, action.taskId, null)
                    }
                }

                is ProjectDetailAction.OnSubTaskClick -> {
                    state.project?.let {
                        onEditTextClick(
                            true,
                            it.projectId,
                            EditTextTarget.SUBTASK,
                            action.taskId,
                            action.subTaskId,
                        )
                    }
                }

                is ProjectDetailAction.OnAddSubTaskClick -> {
                    state.project?.let {
                        onEditTextClick(true, it.projectId, EditTextTarget.NEW_SUBTASK, action.taskId, null)
                    }
                }

                is ProjectDetailAction.OnProjectSessionCardClick -> {
                    onProjectTaskClick(action.projectSessionId)
                }

                else -> {
                    Unit
                }
            }
            viewModel.onAction(action)
        },
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    state: ProjectDetailState,
    onAction: (ProjectDetailAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Reordering is an edit-mode affordance: outside it the cards show timer buttons, not grips.
    val reorderEnabled = state.isEditMode
    val dragDropState =
        rememberReorderableListState(
            lazyListState = listState,
            onMove = { fromKey, toKey ->
                onAction(ProjectDetailAction.OnTaskReorderMove(fromTaskId = fromKey, toTaskId = toKey))
            },
        )
    // One instance, shared by the app bar and the nested-scroll connection below. Calling the
    // helper again at either site would orphan a behavior and freeze the list.
    val scrollBehavior =
        rememberCollapsibleScrollBehavior(
            listState = listState,
            pinned = state.isEditMode,
        )

    // Composited against the Scaffold background so it is fully opaque: the top bar uses this
    // colour too, and list items scrolling underneath must not show through it.
    val headerColor =
        state.projectColor
            ?.copy(alpha = 0.12f)
            ?.compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
            ?: MaterialTheme.colorScheme.onSurface
    // The header paints edge-to-edge behind the status bar, so it has to reserve the space
    // the bar and the status bar occupy itself. Both values are constant, unlike the
    // Scaffold's top padding, which shrinks frame by frame as the bar collapses.
    val headerTopInset =
        WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() +
            TopAppBarDefaults.TopAppBarExpandedHeight

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (state.isEditMode) "EDIT PROJECT" else "PROJECT DETAILS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isEditMode) {
                            onAction(ProjectDetailAction.OnCloseAndCancelClick)
                        } else {
                            onAction(ProjectDetailAction.OnBackClick)
                        }
                    }) {
                        Icon(
                            if (state.isEditMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (state.isEditMode) "Cancel" else "Back",
                        )
                    }
                },
                actions = {
                    // Hidden in edit mode: leaving the screen mid-edit would drop the changes.
                    if (!state.isEditMode) {
                        IconButton(onClick = {
                            onAction(ProjectDetailAction.OnDailyOverviewClick(OPEN_ON_TODAY))
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = stringResource(Res.string.daily_overview_title),
                            )
                        }
                    }
                    IconButton(onClick = {
                        if (state.isEditMode) {
                            onAction(ProjectDetailAction.OnSaveClick)
                        } else {
                            onAction(ProjectDetailAction.OnEditModeClick)
                        }
                    }) {
                        Icon(
                            if (state.isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription =
                                stringResource(
                                    if (state.isEditMode) Res.string.save else Res.string.edit,
                                ),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = headerColor,
                        scrolledContainerColor = headerColor,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = WindowInsets.safeDrawing,
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 1. Header
                item {
                    Column(
                        modifier =
                            Modifier
                                .background(
                                    color = headerColor,
                                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                                ).padding(top = headerTopInset),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProjectHeader(
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp),
                            title = state.titleText ?: stringResource(Res.string.title),
                            description = state.descriptionText ?: stringResource(Res.string.description),
                            onAction = onAction,
                            isEditMode = state.isEditMode,
                            projectId = state.project.projectId,
                        )
                        if (state.isEditMode) {
                            ColorInfoCard(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                label = stringResource(Res.string.select_project_color),
                                colorValue = state.projectColor ?: Color.Cyan,
                                hexCode = state.selectedColorHex,
                                isEditMode = state.isEditMode,
                                onClick = { onAction(ProjectDetailAction.OnToggleColorPicker) },
                            )
                            TextColorToggle(
                                modifier =
                                    Modifier
                                        .padding(horizontal = 16.dp),
                                useLightTextColor = state.useLightTextColor,
                                onToggle = { onAction(ProjectDetailAction.OnUseLightTextColorToggled(it)) },
                            )
                        }
                        DurationHeroCard(
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                            label = stringResource(Res.string.project_duration),
                            totalDuration = state.project.totalProjectDuration,
                            projectColor = state.projectColor ?: MaterialTheme.colorScheme.onSurface,
                            useLightTextColor = state.useLightTextColor,
                            onStartStopClick = {
                                // Logic for project-wide tracker if needed
                                onAction(ProjectDetailAction.OnStartTrackerClick)
                            },
                            // The number keeps ticking for a foreign timer and is right; when it
                            // goes stale it stops and is merely the last thing known to be true.
                            // Either way the card has to say whose timer it is.
                            caption =
                                when {
                                    state.isRunningTimerStale -> {
                                        stringResource(Res.string.timer_stale_on_another_device)
                                    }

                                    state.isRunningTimerForeign -> {
                                        stringResource(Res.string.timer_running_on_another_device)
                                    }

                                    else -> {
                                        null
                                    }
                                },
                        )
                    }
                }

                // 2. Info Grid
                item {
                    InfoGrid(
                        modifier =
                            Modifier
                                .padding(vertical = 16.dp),
                        startDate = state.project.startDateTimeUtc,
                        lastActive = state.project.startDateTimeUtc,
                        perDayStrip = state.perDayStrip,
                        projectColor = state.projectColor ?: MaterialTheme.colorScheme.primary,
                        doneTaskCount = state.project.doneTaskCount,
                        taskCount =
                            state.project.projectTasks?.size ?: 0,
                        taskProgress = { state.project.taskProgress },
                        onAction = onAction,
                    )
                }

                // 4. Sessions Header
                item {
                    TasksHeader(
                        modifier =
                            Modifier
                                .padding(horizontal = 16.dp),
                        onAddClick = {
                            onAction(ProjectDetailAction.OnToggleAddNewProjectSessionBottomSheet)
                        },
                        addButtonContainerColor = state.projectColor ?: MaterialTheme.colorScheme.primary,
                        addButtonContentColor = if (state.useLightTextColor) Color.White else Color.Black,
                    )
                }

                // 5. Session Items
                itemsIndexed(
                    items = state.project.projectTasks.orEmpty(),
                    // Stable String keys: the reorder state hit-tests on them, and every item above
                    // this one is keyed by position, which is what keeps a drag inside the task list.
                    key = { _, task -> task.projectTaskId },
                ) { index, session ->
                    // The dragged card (and the one settling back after release) drives its own
                    // translation and rides above the rest; every other card animates to its new
                    // slot via animateItem().
                    val isActive =
                        session.projectTaskId == dragDropState.draggingItemKey ||
                            session.projectTaskId == dragDropState.settlingItemKey
                    val cardModifier =
                        if (isActive) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer {
                                    translationY =
                                        if (session.projectTaskId == dragDropState.draggingItemKey) {
                                            dragDropState.draggingItemOffset
                                        } else {
                                            dragDropState.settlingItemOffset
                                        }
                                }
                        } else {
                            Modifier.animateItem()
                        }
                    TaskItemCard(
                        modifier =
                            cardModifier
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
                        onToggleExpand = {
                            onAction(ProjectDetailAction.OnToggleTaskExpanded(session.projectTaskId))
                        },
                        onTaskTitleClick = {
                            onAction(ProjectDetailAction.OnTaskTitleClick(session.projectTaskId))
                        },
                        onAddSubTaskClick = {
                            onAction(ProjectDetailAction.OnAddSubTaskClick(session.projectTaskId))
                        },
                        onSubTaskClick = { subTaskId ->
                            onAction(ProjectDetailAction.OnSubTaskClick(session.projectTaskId, subTaskId))
                        },
                        isReorderable = reorderEnabled,
                        onReorderDragStart = { dragDropState.onDragStart(session.projectTaskId) },
                        onReorderDrag = { dragAmountY -> dragDropState.onDrag(dragAmountY) },
                        onReorderDragEnd = {
                            // Only a gesture that actually moved something is worth persisting.
                            if (dragDropState.hasMoved) onAction(ProjectDetailAction.OnTaskReorderCommit)
                            dragDropState.onDragEnd()
                        },
                        onReorderDragCancel = {
                            onAction(ProjectDetailAction.OnTaskReorderCancel)
                            dragDropState.onDragCancel()
                        },
                        onSubTaskReorderMove = { fromSubTaskId, toSubTaskId ->
                            onAction(
                                ProjectDetailAction.OnSubTaskReorderMove(
                                    taskId = session.projectTaskId,
                                    fromSubTaskId = fromSubTaskId,
                                    toSubTaskId = toSubTaskId,
                                ),
                            )
                        },
                        onSubTaskReorderCommit = {
                            onAction(ProjectDetailAction.OnSubTaskReorderCommit(session.projectTaskId))
                        },
                        onSubTaskReorderCancel = {
                            onAction(ProjectDetailAction.OnSubTaskReorderCancel)
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
    if (state.isAddNewProjectTaskBottomSheetVisible) {
        AddNewProjectTaskBottomSheet(
            textFieldState = state.addProjectTaskTextFieldState,
            onAction = onAction,
        )
    }
    if (state.isColorPickerVisible) {
        TrackyColorPicker(
            currentColor = state.projectColor ?: Color.Cyan,
            onCancel = { onAction(ProjectDetailAction.OnToggleColorPicker) },
            onSave = { onAction(ProjectDetailAction.OnColorChanged(it)) },
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
            },
        )
    }
}

@Composable
private fun ProjectHeader(
    title: String,
    description: String,
    onAction: (ProjectDetailAction) -> Unit,
    isEditMode: Boolean,
    projectId: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color =
                        if (isEditMode) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            Color.Transparent
                        },
                    shape = RoundedCornerShape(16.dp),
                ).border(
                    BorderStroke(
                        width = 1.dp,
                        color =
                            if (isEditMode) {
                                MaterialTheme.colorScheme.outlineVariant
                            } else {
                                Color.Transparent
                            },
                    ),
                    shape = RoundedCornerShape(16.dp),
                ).clickable {
                    projectId?.let {
                        onAction(
                            ProjectDetailAction.OnProjectEditTextClick(
                                isEditMode = isEditMode,
                                projectId = it,
                            ),
                        )
                    }
                },
    ) {
        Text(
            modifier =
                Modifier
                    .padding(16.dp),
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            text = description,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoGrid(
    startDate: String,
    lastActive: String,
    perDayStrip: PerDayStripUi?,
    projectColor: Color,
    doneTaskCount: Int,
    taskCount: Int,
    taskProgress: () -> Float,
    onAction: (ProjectDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(
                icon = Icons.Outlined.DateRange,
                label = stringResource(Res.string.start_date),
                value = startDate,
                modifier = Modifier.weight(1f),
            )
            InfoCard(
                icon = Icons.Outlined.History,
                label = stringResource(Res.string.last_active),
                value = lastActive,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Null for a project with no banked time: an empty strip would say less than no strip.
            // The column's spacedBy(12.dp) supplies the gaps, so the card needs no padding of its own.
            perDayStrip?.let { strip ->
                PerDayCard(
                    days = strip.days,
                    busiestDayLabel = strip.busiestDayLabel,
                    projectColor = projectColor,
                    // Each tile carries its own date, so the overview opens on the day that was
                    // tapped. The top-bar icon sends OPEN_ON_TODAY instead.
                    onDayClick = { onAction(ProjectDetailAction.OnDailyOverviewClick(it.toEpochDays())) },
                )
            }
        }

        InfoCard(
            modifier =
                Modifier
                    .fillMaxWidth(),
            icon = Icons.Outlined.GridView,
            label = stringResource(Res.string.tasks_completed),
            value =
                stringResource(
                    Res.string.task_completed_count,
                    doneTaskCount,
                    taskCount,
                ),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                // Lambda overload: the progress is read in the draw phase, so animating it
                // later will not recompose the card.
                progress = taskProgress,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                color = projectColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun TextColorToggle(
    useLightTextColor: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.light_text_color),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = useLightTextColor,
                onCheckedChange = onToggle,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
            )
        }
    }
}

@Composable
private fun TasksHeader(
    onAddClick: () -> Unit,
    addButtonContainerColor: Color,
    addButtonContentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(Res.string.tasks), style = MaterialTheme.typography.headlineMedium)
        FilledIconButton(
            onClick = onAddClick,
            shape = RoundedCornerShape(16.dp),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = addButtonContainerColor,
                    contentColor = addButtonContentColor,
                ),
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_task))
        }
    }
}

@Preview(device = Devices.PIXEL_9_PRO)
@Composable
private fun ProjectDetailScreenPreview() {
    TrackyTheme {
        ProjectDetailScreen(
            onAction = {},
            state =
                ProjectDetailState(
                    project =
                        ProjectUi(
                            projectId = "1",
                            title = "Project One",
                            description = "Description 1",
                            color = Color.Red,
                            totalDurationMillis = 36_000_000L,
                            startDateTimeUtc = "2023-01-01T00:00:00Z",
                            isFinished = false,
                            endDateTimeUtc = null,
                            projectTasks = projectSessionsPreview,
                        ),
                    isEditMode = false,
                ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview(device = Devices.PIXEL_9_PRO)
@Composable
private fun ProjectDetailScreenEditModePreview() {
    TrackyTheme {
        ProjectDetailScreen(
            onAction = {},
            state =
                ProjectDetailState(
                    project =
                        ProjectUi(
                            projectId = "1",
                            title = "Project One",
                            description = "Description 1",
                            color = Color.Yellow,
                            totalDurationMillis = 36_000_000L,
                            startDateTimeUtc = "2023-01-01T00:00:00Z",
                            isFinished = false,
                            endDateTimeUtc = null,
                            projectTasks = projectSessionsPreview,
                        ),
                    isEditMode = true,
                ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private val projectSessionsPreview =
    listOf(
        ProjectTaskUi(
            projectTaskId = "1",
            title = "This is session One",
            description = "Description 1",
            durationMillis = 36_000_000L,
            formattedStateDateTime = "2023-01-01T00:00:00Z",
            formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
            isTimerRunning = false,
            subTasks = emptyList(),
            isFinished = false,
        ),
        ProjectTaskUi(
            projectTaskId = "2",
            title = "This is session Two",
            description = "Description 2",
            durationMillis = 36_000_000L,
            formattedStateDateTime = "2023-01-01T00:00:00Z",
            formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
            isTimerRunning = false,
            subTasks =
                listOf(
                    ProjectSubTaskUi(
                        projectSubTaskId = "1",
                        title = "SubTask 1",
                        description = "Description SubTask 1",
                        durationMillis = 20_153_000L,
                        formattedStartDateTime = "23.08.2026, 10:15",
                        formattedEndDateTimeUtc = null,
                        isTimerRunning = false,
                        isFinished = false,
                    ),
                    ProjectSubTaskUi(
                        projectSubTaskId = "2",
                        title = "SubTask 2",
                        description = "Description SubTask 2",
                        durationMillis = 20_153_000L,
                        formattedStartDateTime = "23.08.2026, 13:15",
                        formattedEndDateTimeUtc = null,
                        isTimerRunning = true,
                        isFinished = false,
                    ),
                    ProjectSubTaskUi(
                        projectSubTaskId = "3",
                        title = "SubTask 3",
                        description = "Description SubTask 3",
                        durationMillis = 20_153_000L,
                        formattedStartDateTime = "23.08.2026, 16:30",
                        formattedEndDateTimeUtc = "23.08.2026, 17:00",
                        isTimerRunning = false,
                        isFinished = true,
                    ),
                ),
            isFinished = false,
        ),
        ProjectTaskUi(
            projectTaskId = "3",
            title = "This is session Three",
            description = null,
            durationMillis = 36_000_000L,
            formattedStateDateTime = "2023-01-01T00:00:00Z",
            formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
            isTimerRunning = false,
            subTasks = emptyList(),
            isFinished = false,
        ),
    )
