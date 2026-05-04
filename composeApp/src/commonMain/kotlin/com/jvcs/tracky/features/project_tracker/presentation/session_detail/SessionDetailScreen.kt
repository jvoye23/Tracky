package com.jvcs.tracky.features.project_tracker.presentation.session_detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SessionDetailScreenRoot(
    sessionId: String,
    navigateBack: () -> Unit,
    viewModel: SessionDetailViewModel = koinViewModel(parameters = { parametersOf(sessionId) })
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SessionDetailScreen(
        state = state,
        onAction = { action ->
            if (action == SessionDetailAction.OnBackClick) {
                navigateBack()
            }
            viewModel.onAction(action)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    state: SessionDetailState,
    onAction: (SessionDetailAction) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SESSION DETAILS") },
                navigationIcon = {
                    IconButton(onClick = { onAction(SessionDetailAction.OnBackClick) }) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Editing
            OutlinedTextField(
                value = state.titleText,
                onValueChange = { onAction(SessionDetailAction.OnTitleChanged(it)) },
                label = { Text("Session Title") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { onAction(SessionDetailAction.OnSaveTitle) }) {
                        Text("Save")
                    }
                }
            )

            // Timer Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.session?.formattedDuration ?: "00:00:00",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onAction(SessionDetailAction.OnToggleTimer) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isTimerRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            if (state.isTimerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.isTimerRunning) "Stop Timer" else "Resume Timer")
                    }
                }
            }

            Text(
                "Daily Statistics",
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
