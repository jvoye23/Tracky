package com.jvcs.tracky.features.project.presentation.strandedtimer

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.designsystem.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Hosts the stranded-timer review above whatever screen the app came back to.
 *
 * App level rather than per-screen, because the back stack is restored across process death — the
 * very event that strands a timer — so the user lands wherever they were: overview, project detail,
 * task detail, daily overview. Hanging this off one screen would mean five copies and still miss
 * the one that was forgotten.
 */
@Composable
fun StrandedTimerDialogHost(modifier: Modifier = Modifier, viewModel: StrandedTimerViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is StrandedTimerEvent.Error -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.error.asStringAsync(),
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }

    val current = state.current ?: return

    // One emitter: the dialog draws in its own window, the snackbar host in this Box.
    Box(modifier = modifier) {
        StrandedTimerDialog(
            timer = current,
            remainingCount = (state.pending.size - 1).coerceAtLeast(0),
            isEditingDuration = state.isEditingDuration,
            editDurationState = state.editDurationState,
            isResolving = state.isResolving,
            onAction = viewModel::onAction,
        )
        SnackbarHost(hostState = snackbarHostState)
    }
}
