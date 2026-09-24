package com.jvcs.tracky.features.project.presentation.timer_permission

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jvcs.tracky.design_system.theme.TrackyTheme
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.confirm
import tracky.composeapp.generated.resources.timer_notification_permission_message
import tracky.composeapp.generated.resources.timer_notification_permission_open_settings
import tracky.composeapp.generated.resources.timer_notification_permission_title

/**
 * Explains what a refused notification permission costs.
 *
 * Dismissible from outside, unlike the stranded-timer review: nothing is being held back here, and
 * the timer keeps running whichever way this is answered.
 */
@Composable
fun TimerNotificationPermissionDialog(
    onAction: (TimerNotificationPermissionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { onAction(TimerNotificationPermissionAction.OnConfirm) },
        title = { Text(text = stringResource(Res.string.timer_notification_permission_title)) },
        text = { Text(text = stringResource(Res.string.timer_notification_permission_message)) },
        confirmButton = {
            TextButton(onClick = { onAction(TimerNotificationPermissionAction.OnConfirm) }) {
                Text(text = stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(TimerNotificationPermissionAction.OnOpenAppSettings) }) {
                Text(text = stringResource(Res.string.timer_notification_permission_open_settings))
            }
        },
    )
}

@Preview
@Composable
private fun TimerNotificationPermissionDialogPreview() {
    TrackyTheme {
        TimerNotificationPermissionDialog(onAction = {})
    }
}
