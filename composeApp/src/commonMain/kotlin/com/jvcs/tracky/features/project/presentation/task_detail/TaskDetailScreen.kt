package com.jvcs.tracky.features.project.presentation.task_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.dailyStatistics) { statistic ->
                    ListItem(
                        headlineContent = { Text(statistic.formattedDate) },
                        trailingContent = { Text(statistic.formattedDuration, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }
    }
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
