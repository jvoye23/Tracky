package com.jvcs.tracky.features.project.presentation.timer_permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Hosts the notification-permission ask above whatever screen started the timer.
 *
 * Composing this is what starts the ViewModel observing; the prompt itself only follows once a timer
 * is actually running.
 */
@Composable
fun TimerNotificationPermissionDialogHost(viewModel: TimerNotificationPermissionViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (!state.showDeniedDialog) return

    TimerNotificationPermissionDialog(onAction = viewModel::onAction)
}
