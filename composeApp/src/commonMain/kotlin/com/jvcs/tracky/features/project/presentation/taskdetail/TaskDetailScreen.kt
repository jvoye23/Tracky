package com.jvcs.tracky.features.project.presentation.taskdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.designsystem.components.DurationHeroCard
import com.jvcs.tracky.designsystem.theme.SampleProjectColors
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.taskdetail.model.DailyStatistic
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.daily_sessions
import tracky.composeapp.generated.resources.date
import tracky.composeapp.generated.resources.description
import tracky.composeapp.generated.resources.duration
import tracky.composeapp.generated.resources.edit_task_uppercase
import tracky.composeapp.generated.resources.end_time
import tracky.composeapp.generated.resources.start_time
import tracky.composeapp.generated.resources.start_timer
import tracky.composeapp.generated.resources.stop_timer
import tracky.composeapp.generated.resources.task_details
import tracky.composeapp.generated.resources.task_duration
import tracky.composeapp.generated.resources.title

@Composable
fun TaskDetailScreenRoot(
    taskId: String,
    navigateBack: () -> Unit,
    onEditTextClick: (isEditMode: Boolean, projectId: String, taskId: String) -> Unit,
    viewModel: TaskDetailViewModel = koinViewModel(parameters = { parametersOf(taskId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TaskDetailScreen(
        state = state,
        onAction = { action ->
            when (action) {
                TaskDetailAction.OnBackClick -> {
                    navigateBack()
                }

                // Same contract as the project header: outside edit mode the editor opens
                // read-only, in edit mode it opens straight into editing.
                TaskDetailAction.OnHeaderClick -> {
                    state.projectId?.let { projectId ->
                        onEditTextClick(state.isEditMode, projectId, taskId)
                    }
                }

                else -> {
                    Unit
                }
            }
            viewModel.onAction(action)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    state: TaskDetailState,
    onAction: (TaskDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Composited so it is fully opaque, matching Project Detail: the top bar and the header share
    // this colour and must read as one surface.
    val headerColor =
        state.projectColor
            ?.copy(alpha = 0.12f)
            ?.compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
            ?: MaterialTheme.colorScheme.surfaceContainerLow

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TaskDetailTopBar(
                isEditMode = state.isEditMode,
                headerColor = headerColor,
                onAction = onAction,
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = headerColor,
                            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskHeader(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = state.task?.title ?: stringResource(Res.string.title),
                    description =
                        state.task?.description?.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.description),
                    isEditMode = state.isEditMode,
                    onClick = { onAction(TaskDetailAction.OnHeaderClick) },
                )
                DurationHeroCard(
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                    label = stringResource(Res.string.task_duration),
                    totalDuration = state.task?.displayDuration ?: "00:00:00",
                    projectColor = state.projectColor ?: MaterialTheme.colorScheme.primary,
                    useLightTextColor = state.useLightTextColor,
                    onStartStopClick = { onAction(TaskDetailAction.OnToggleTimer) },
                )
            }

            // Timer
            TimerToggleRow(
                isTimerRunning = state.isTimerRunning,
                onToggleTimer = { onAction(TaskDetailAction.OnToggleTimer) },
            )

            // Daily sessions
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(Res.string.daily_sessions),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Column(modifier = Modifier.weight(1f)) {
                SessionRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    date = stringResource(Res.string.date),
                    startTime = stringResource(Res.string.start_time),
                    endTime = stringResource(Res.string.end_time),
                    duration = stringResource(Res.string.duration),
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(
                        items = state.dailyStatistics,
                        // A multi-day interval's slices share an id, but never a date.
                        key = { _, statistic -> statistic.intervalId + statistic.formattedDate },
                    ) { index, statistic ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 1.dp,
                            )
                        }
                        SessionRow(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            date = statistic.formattedDate,
                            startTime = statistic.formattedStartTime,
                            endTime = statistic.formattedEndTime,
                            duration = statistic.formattedDuration,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailTopBar(
    isEditMode: Boolean,
    headerColor: Color,
    onAction: (TaskDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text =
                    if (isEditMode) {
                        stringResource(Res.string.edit_task_uppercase)
                    } else {
                        stringResource(Res.string.task_details)
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            IconButton(onClick = {
                if (isEditMode) {
                    onAction(TaskDetailAction.OnCloseEditModeClick)
                } else {
                    onAction(TaskDetailAction.OnBackClick)
                }
            }) {
                Icon(
                    if (isEditMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (isEditMode) "Close" else "Back",
                )
            }
        },
        actions = {
            IconButton(onClick = {
                if (isEditMode) {
                    onAction(TaskDetailAction.OnCloseEditModeClick)
                } else {
                    onAction(TaskDetailAction.OnEditModeClick)
                }
            }) {
                Icon(
                    if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (isEditMode) "Done" else "Edit",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = headerColor),
    )
}

@Composable
private fun TimerToggleRow(
    isTimerRunning: Boolean,
    onToggleTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = onToggleTimer,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (isTimerRunning) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                ),
        ) {
            Icon(
                if (isTimerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isTimerRunning) {
                    stringResource(
                        Res.string.stop_timer,
                    )
                } else {
                    stringResource(Res.string.start_timer)
                },
            )
        }
    }
}

@Composable
private fun TaskHeader(
    title: String,
    description: String,
    isEditMode: Boolean,
    onClick: () -> Unit,
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
                ).clickable(onClick = onClick),
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
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

/** One line of the sessions table. The header uses it too, so the columns line up. */
@Composable
private fun SessionRow(
    date: String,
    startTime: String,
    endTime: String,
    duration: String,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TableCell(text = date, weight = 1.4f, fontWeight = fontWeight)
        TableCell(text = startTime, weight = 1f, fontWeight = fontWeight)
        TableCell(text = endTime, weight = 1f, fontWeight = fontWeight)
        TableCell(text = duration, weight = 1.2f, fontWeight = fontWeight, textAlign = TextAlign.End)
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    fontWeight: FontWeight?,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        modifier = Modifier.weight(weight),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val previewState =
    TaskDetailState(
        task =
            ProjectTaskUi(
                projectTaskId = "task-1",
                title = "Design review",
                description = "Walk through the new task detail layout with the team.",
                durationMillis = 5_400_000L,
                formattedStateDateTime = "",
                formattedEndDateTimeUtc = "",
                isTimerRunning = false,
                subTasks = emptyList(),
                isFinished = false,
            ),
        projectId = "project-1",
        projectColor = SampleProjectColors.Indigo,
        useLightTextColor = true,
        dailyStatistics =
            listOf(
                DailyStatistic("i3", "2026-09-18", "14:00", "15:00", "01:00:00"),
                DailyStatistic("i2", "2026-09-17", "23:40", "24:00", "00:20:00"),
                DailyStatistic("i1", "2026-09-17", "09:30", "09:40", "00:10:00"),
            ),
    )

@Preview
@Composable
private fun TaskDetailScreenPreview() {
    TrackyTheme {
        TaskDetailScreen(
            state = previewState,
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun TaskDetailScreenEditModePreview() {
    TrackyTheme {
        TaskDetailScreen(
            state = previewState.copy(isEditMode = true),
            onAction = {},
        )
    }
}
