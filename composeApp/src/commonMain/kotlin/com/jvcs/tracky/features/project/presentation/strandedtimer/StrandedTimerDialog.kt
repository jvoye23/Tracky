package com.jvcs.tracky.features.project.presentation.strandedtimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.util.formatDurationHoursMinutes
import com.jvcs.tracky.features.project.domain.timer.StrandedTimer
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.cancel
import tracky.composeapp.generated.resources.save
import tracky.composeapp.generated.resources.stranded_timer_discard
import tracky.composeapp.generated.resources.stranded_timer_duration_label
import tracky.composeapp.generated.resources.stranded_timer_edit
import tracky.composeapp.generated.resources.stranded_timer_keep
import tracky.composeapp.generated.resources.stranded_timer_message
import tracky.composeapp.generated.resources.stranded_timer_not_counted
import tracky.composeapp.generated.resources.stranded_timer_remaining
import tracky.composeapp.generated.resources.stranded_timer_subtask_line
import tracky.composeapp.generated.resources.stranded_timer_title
import kotlin.time.Instant

/** "Sep 9, 14:57" — enough to recognise the session without a full timestamp. */
private val startedAtFormat =
    LocalDateTime.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        chars(" ")
        day(Padding.NONE)

        chars(", ")
        hour()
        chars(":")
        minute()
    }

/**
 * Asks what a timer left running was worth.
 *
 * Deliberately not dismissible by tapping outside: the time is held out of every total until this
 * is answered, so a dialog the user can swipe away would silently lose work. Discard is right
 * there for anyone who does not want it.
 */
@Composable
fun StrandedTimerDialog(
    timer: StrandedTimer,
    remainingCount: Int,
    isEditingDuration: Boolean,
    editDurationState: TextFieldState,
    isResolving: Boolean,
    onAction: (StrandedTimerAction) -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val startedAt = timer.startedAt.toLocalDateTime(timeZone).format(startedAtFormat)
    val offered = formatDurationHoursMinutes(timer.proposedDuration)

    AlertDialog(
        modifier = modifier,
        onDismissRequest = {}, // see the KDoc
        title = { Text(text = stringResource(Res.string.stranded_timer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text =
                        stringResource(
                            Res.string.stranded_timer_message,
                            timer.taskTitle,
                            timer.projectTitle,
                            startedAt,
                            offered,
                        ),
                )
                timer.subTaskTitle?.let {
                    Text(
                        text = stringResource(Res.string.stranded_timer_subtask_line, it),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (timer.keepingWouldNotBeCounted) {
                    Text(
                        text = stringResource(Res.string.stranded_timer_not_counted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (isEditingDuration) {
                    OutlinedTextField(
                        state = editDurationState,
                        label = { Text(text = stringResource(Res.string.stranded_timer_duration_label)) },
                        lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                    )
                }
                if (remainingCount > 0) {
                    Text(
                        text = stringResource(Res.string.stranded_timer_remaining, remainingCount),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            if (isEditingDuration) {
                TextButton(
                    enabled = !isResolving,
                    onClick = { onAction(StrandedTimerAction.OnConfirmEditedDuration) },
                ) {
                    Text(text = stringResource(Res.string.save))
                }
            } else {
                TextButton(
                    enabled = !isResolving,
                    onClick = { onAction(StrandedTimerAction.OnKeep) },
                ) {
                    Text(text = stringResource(Res.string.stranded_timer_keep, offered))
                }
            }
        },
        dismissButton = {
            if (isEditingDuration) {
                TextButton(
                    enabled = !isResolving,
                    onClick = { onAction(StrandedTimerAction.OnCancelEditDuration) },
                ) {
                    Text(text = stringResource(Res.string.cancel))
                }
            } else {
                Column {
                    TextButton(
                        enabled = !isResolving,
                        onClick = { onAction(StrandedTimerAction.OnBeginEditDuration) },
                    ) {
                        Text(text = stringResource(Res.string.stranded_timer_edit))
                    }
                    TextButton(
                        enabled = !isResolving,
                        onClick = { onAction(StrandedTimerAction.OnDiscard) },
                    ) {
                        Text(
                            text = stringResource(Res.string.stranded_timer_discard),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )
}

private val previewTimer =
    StrandedTimer(
        taskIntervalId = "i1",
        subTaskIntervalId = null,
        taskId = "t1",
        taskTitle = "Project Detail Screen",
        projectTitle = "Tracky App",
        subTaskTitle = null,
        startedAt = Instant.fromEpochMilliseconds(1_788_000_000_000),
        proposedEndAt = Instant.fromEpochMilliseconds(1_788_271_266_120),
    )

@Preview
@Composable
private fun StrandedTimerDialogPreview() {
    TrackyTheme {
        StrandedTimerDialog(
            timer = previewTimer,
            remainingCount = 0,
            isEditingDuration = false,
            editDurationState = TextFieldState(),
            isResolving = false,
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun StrandedTimerDialogEditingPreview() {
    TrackyTheme {
        StrandedTimerDialog(
            timer = previewTimer.copy(subTaskTitle = "Per-day strip", keepingWouldNotBeCounted = true),
            remainingCount = 2,
            isEditingDuration = true,
            editDurationState = TextFieldState("75:21"),
            isResolving = false,
            onAction = {},
        )
    }
}
