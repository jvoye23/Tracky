package com.jvcs.tracky.features.project.presentation.task_detail

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.design_system.components.DurationHeroCard
import com.jvcs.tracky.design_system.theme.TrackyTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.daily_sessions
import tracky.composeapp.generated.resources.date
import tracky.composeapp.generated.resources.duration
import tracky.composeapp.generated.resources.end_time
import tracky.composeapp.generated.resources.start_time
import tracky.composeapp.generated.resources.save
import tracky.composeapp.generated.resources.start_timer
import tracky.composeapp.generated.resources.stop_timer
import tracky.composeapp.generated.resources.task_details
import tracky.composeapp.generated.resources.task_duration
import tracky.composeapp.generated.resources.title

@Composable
fun TaskDetailScreenRoot(
    taskId: String,
    navigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = koinViewModel(parameters = { parametersOf(taskId) })
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TaskDetailScreen(
        state = state,
        onAction = { action ->
            if (action == TaskDetailAction.OnBackClick) {
                navigateBack()
            }
            viewModel.onAction(action)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    state: TaskDetailState,
    onAction: (TaskDetailAction) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.task_details),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(TaskDetailAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title Editing
            OutlinedTextField(
                value = state.titleText,
                onValueChange = { onAction(TaskDetailAction.OnTitleChanged(it)) },
                label = {
                    Text(text = stringResource(Res.string.title))
                },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { onAction(TaskDetailAction.OnSaveTitle) }) {
                        Text(text = stringResource(Res.string.save))
                    }
                }
            )

            // Timer Section

            DurationHeroCard(
                label = stringResource(Res.string.task_duration),
                totalDuration = state.task?.formattedDuration ?: "00:00:00",
                projectColor = Color.Blue, //TODO Get project color
                useLightTextColor = true, // TODO Get project text color
                onStartStopClick = {}
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {

                Button(
                    onClick = { onAction(TaskDetailAction.OnToggleTimer) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isTimerRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (state.isTimerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (state.isTimerRunning) stringResource(Res.string.stop_timer) else stringResource(Res.string.start_timer))
                }
            }

            Text(
                text = stringResource(Res.string.daily_sessions),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Column(modifier = Modifier.weight(1f)) {
                SessionRow(
                    date = stringResource(Res.string.date),
                    startTime = stringResource(Res.string.start_time),
                    endTime = stringResource(Res.string.end_time),
                    duration = stringResource(Res.string.duration),
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(thickness = 1.dp)
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(
                        items = state.dailyStatistics,
                        // A multi-day interval's slices share an id, but never a date.
                        key = { _, statistic -> statistic.intervalId + statistic.formattedDate }
                    ) { index, statistic ->
                        if (index > 0) {
                            HorizontalDivider(thickness = 1.dp)
                        }
                        SessionRow(
                            date = statistic.formattedDate,
                            startTime = statistic.formattedStartTime,
                            endTime = statistic.formattedEndTime,
                            duration = statistic.formattedDuration
                        )
                    }
                }
            }
        }
    }
}

/** One line of the sessions table. The header uses it too, so the columns line up. */
@Composable
private fun SessionRow(
    modifier: Modifier = Modifier,
    date: String,
    startTime: String,
    endTime: String,
    duration: String,
    fontWeight: FontWeight? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        modifier = Modifier.weight(weight),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Preview
@Composable
private fun TaskDetailScreenPreview() {
    TrackyTheme {
        TaskDetailScreen(
            state = TaskDetailState(),
            onAction = {}
        )
    }
}
