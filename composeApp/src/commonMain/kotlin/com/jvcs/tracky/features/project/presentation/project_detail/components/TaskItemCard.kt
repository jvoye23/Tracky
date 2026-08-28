package com.jvcs.tracky.features.project.presentation.project_detail.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvcs.tracky.design_system.Icon_ChevronDown
import com.jvcs.tracky.design_system.Icon_ChevronUp
import com.jvcs.tracky.design_system.Icon_Plus
import com.jvcs.tracky.design_system.Icon_Timer
import com.jvcs.tracky.design_system.Icon_Trash
import com.jvcs.tracky.design_system.components.TrackyCheckbox
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.models.ProjectSubTaskUi
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.add_subtask
import tracky.composeapp.generated.resources.delete
import tracky.composeapp.generated.resources.hide_subtasks
import tracky.composeapp.generated.resources.show_subtasks
import tracky.composeapp.generated.resources.subtask_progress
import tracky.composeapp.generated.resources.start_timer
import tracky.composeapp.generated.resources.stop_timer

/**
 * Returns the pulse alpha as a [State] so callers can read it inside a `graphicsLayer {}`
 * lambda — a draw-phase read that invalidates only the layer instead of recomposing the card
 * on every animation frame. When [enabled] is false no transition is created at all, so idle
 * cards don't keep a frame loop alive.
 */
@Composable
private fun rememberPulseAlpha(enabled: Boolean): State<Float> {
    val idle = remember { mutableFloatStateOf(1f) }
    if (!enabled) return idle

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    return infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "alpha"
    )
}


/**
 * The play/pause tile shown at the start of a task or subtask row.
 *
 * [pulseAlpha] is a lambda, not a `Float`, on purpose: it is only invoked inside the
 * `graphicsLayer {}` block so the animated value is read in the draw phase. Passing the value
 * itself would make every caller recompose on every animation frame.
 */
@Composable
private fun TimerToggleButton(
    isTimerRunning: Boolean,
    isFinished: Boolean,
    projectColor: Color,
    pulseAlpha: () -> Float,
    buttonSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer {
                alpha = if (isTimerRunning) pulseAlpha() else 1f
            },
        enabled = !isFinished,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            contentColor = if (isTimerRunning) Color.White else projectColor,
            containerColor = if (isTimerRunning) projectColor else projectColor.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.outline,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        shape = if (isTimerRunning) RoundedCornerShape(10.dp) else CircleShape,
    ) {
        Icon(
            imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(
                if (isTimerRunning) Res.string.stop_timer else Res.string.start_timer
            ),
            modifier = Modifier.size(buttonSize * 0.45f)
        )
    }
}

/**
 * The reorder grip shown in place of the timer button in edit mode.
 *
 * Currently decorative — dragging does nothing yet, because subtasks have no persisted order to
 * write back to (ProjectSubTaskEntity has no sortIndex column, unlike ProjectEntity). The
 * description is therefore null: announcing "Reorder" would promise an affordance that is not
 * there. Give it one when the gesture is wired.
 */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.DragHandle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = modifier.size(20.dp)
    )
}

/**
 * Inline rename field. Deliberately a [BasicTextField] rather than TrackyTextField: the design
 * outlines the whole row and shows a bare cursor, so the field itself must carry no label,
 * container or elevation of its own.
 */
@Composable
private fun SubTaskTitleField(
    state: TextFieldState,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        state = state,
        modifier = modifier.focusRequester(focusRequester),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { onCommit() }
    )
}

/** Dashed "+ Subtask" pill closing out the edit-mode card. */
@Composable
private fun AddSubTaskPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icon_Plus,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = stringResource(Res.string.add_subtask),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/** Trash icon on a tinted square, per the edit-mode design. */
@Composable
private fun DeleteTaskButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Icon(
            imageVector = Icon_Trash,
            contentDescription = stringResource(Res.string.delete),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun TaskItemCard(
    modifier: Modifier = Modifier,
    index: Int,
    task: ProjectTaskUi,
    projectColor: Color,
    isEditMode: Boolean,
    onToggleTimer: () -> Unit,
    onDeleteClick: () -> Unit,
    onCardClick: () -> Unit,
    onCheckedChange: () -> Unit,
    onToggleSubTaskTimer: (subTaskId: String) -> Unit,
    onDeleteSubTaskClick: (subTaskId: String) -> Unit,
    onSubTaskCheckedChange: (subTaskId: String) -> Unit,
    isExpanded: Boolean = true,
    onToggleExpanded: () -> Unit = {},
    editingSubTaskId: String? = null,
    editSubTaskTextFieldState: TextFieldState = TextFieldState(),
    isAddingSubTask: Boolean = false,
    onAddSubTaskClick: () -> Unit = {},
    onSubTaskTitleClick: (subTaskId: String, currentTitle: String) -> Unit = { _, _ -> },
    onCommitSubTaskTitle: () -> Unit = {}
) {

    val isPulsing = task.isTimerRunning || task.subTasks.any { it.isTimerRunning }
    val pulseAlpha by rememberPulseAlpha(enabled = isPulsing)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            if (task.isTimerRunning) 1.dp else 0.dp,
            if (task.isTimerRunning) projectColor.copy(0.2f) else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = if (task.isTimerRunning) 4.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main Task
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val textDecoration = if (task.isFinished) TextDecoration.LineThrough else null
                val contentAlpha = if (task.isFinished) 0.4f else 1f

                if (isEditMode) {
                    DragHandle()
                } else {
                    TimerToggleButton(
                        isTimerRunning = task.isTimerRunning,
                        isFinished = task.isFinished,
                        projectColor = projectColor,
                        pulseAlpha = { pulseAlpha },
                        buttonSize = 48.dp,
                        onClick = onToggleTimer
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The design drops the ordinal in edit mode, where the row is handle + title only.
                        if (!isEditMode) {
                            Text(
                                text = index.toString().padStart(2, '0'),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textDecoration = textDecoration
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            modifier = Modifier.weight(1f),
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textDecoration = textDecoration,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!isEditMode) {
                        Text(
                            // Subtask sum once there are subtasks; its own time otherwise.
                            text = task.displayDuration,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (task.isTimerRunning) projectColor
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                            letterSpacing = (-0.5).sp
                        )
                    }
                }
                if (isEditMode) {
                    DeleteTaskButton(onClick = onDeleteClick)
                } else {
                    TrackyCheckbox(
                        checked = task.isFinished,
                        onCheckedChange = { onCheckedChange() }
                    )
                }
            }

            // Progress over the subtasks, with the chevron that collapses them. Edit mode drops
            // the whole row — the design shows only handles, titles and delete buttons there.
            if (task.subTasks.isNotEmpty() && !isEditMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            ,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LinearProgressIndicator(
                            // Lambda overload: the progress is read in the draw phase, so animating it
                            // later will not recompose the card.
                            progress = { task.subTaskProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp),
                            color = projectColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                        Text(
                            text = stringResource(
                                Res.string.subtask_progress,
                                task.doneSubTaskCount,
                                task.subTasks.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if(isExpanded) stringResource(Res.string.hide_subtasks)
                                else stringResource(Res.string.show_subtasks),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        IconButton(
                            onClick = onToggleExpanded,
                            modifier = Modifier
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icon_ChevronUp else Icon_ChevronDown,
                                contentDescription = stringResource(
                                    if (isExpanded) Res.string.hide_subtasks else Res.string.show_subtasks
                                ),
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // optional SubTasks — the design groups them on a tinted band rather than
            // separating them from the main row with a divider.
            // Edit mode always shows them: there is no progress row there to expand them again.
            if ((task.subTasks.isNotEmpty() || isAddingSubTask) && (isExpanded || isEditMode)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(start = 40.dp)
                ) {
                    task.subTasks.forEachIndexed { subTaskIndex, projectSubTaskUi ->
                        val isRenaming =
                            isEditMode && editingSubTaskId == projectSubTaskUi.projectSubTaskId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isRenaming) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    } else Modifier
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val textDecoration =
                                if (projectSubTaskUi.isFinished) TextDecoration.LineThrough else null
                            val contentAlpha = if (projectSubTaskUi.isFinished) 0.4f else 1f

                            if (isEditMode) {
                                DragHandle()
                            } else {
                                TimerToggleButton(
                                    isTimerRunning = projectSubTaskUi.isTimerRunning,
                                    isFinished = projectSubTaskUi.isFinished,
                                    projectColor = projectColor,
                                    pulseAlpha = { pulseAlpha },
                                    buttonSize = 48.dp,
                                    onClick = { onToggleSubTaskTimer(projectSubTaskUi.projectSubTaskId) }
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$index.${subTaskIndex + 1}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                                        textDecoration = textDecoration
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    if (isRenaming) {
                                        SubTaskTitleField(
                                            state = editSubTaskTextFieldState,
                                            onCommit = onCommitSubTaskTitle,
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Text(
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(
                                                    if (isEditMode) {
                                                        Modifier.clickable {
                                                            onSubTaskTitleClick(
                                                                projectSubTaskUi.projectSubTaskId,
                                                                projectSubTaskUi.title
                                                            )
                                                        }
                                                    } else Modifier
                                                ),
                                            text = projectSubTaskUi.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                                            textDecoration = textDecoration,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (!isEditMode) {
                                    Text(
                                        text = projectSubTaskUi.formattedDuration,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (projectSubTaskUi.isTimerRunning) projectColor
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                                        letterSpacing = (-0.5).sp
                                    )
                                }
                            }
                            if (isEditMode) {
                                DeleteTaskButton(
                                    onClick = { onDeleteSubTaskClick(projectSubTaskUi.projectSubTaskId) }
                                )
                            } else {
                                TrackyCheckbox(
                                    checked = projectSubTaskUi.isFinished,
                                    onCheckedChange = {
                                        onSubTaskCheckedChange(projectSubTaskUi.projectSubTaskId)
                                    }
                                )
                            }
                        }
                    }

                    // Draft row for a subtask that does not exist yet. Nothing is written until the
                    // title is committed — the server rejects a blank one.
                    if (isAddingSubTask) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DragHandle()
                            SubTaskTitleField(
                                state = editSubTaskTextFieldState,
                                onCommit = onCommitSubTaskTitle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // The design ends the edit-mode card with the add affordance, below the subtasks.
            if (isEditMode) {
                AddSubTaskPill(
                    onClick = onAddSubTaskClick,
                    modifier = Modifier.padding(start = 16.dp, bottom = 16.dp)
                )
            }
        }
    }
}


private val PreviewProjectColor = Color(0xFF475D92)

private fun previewTask(
    title: String = "Wireframe the settings screen",
    description: String = "Task Description",
    formattedDuration: String = "01:24:07",
    isTimerRunning: Boolean = false,
    isCompleted: Boolean = false
) = ProjectTaskUi(
    projectTaskId = "task-1",
    title = title,
    description = description,
    formattedDuration = formattedDuration,
    formattedStateDateTime = "23.08.2026, 09:15",
    formattedEndDateTimeUtc = if (isCompleted) "23.08.2026, 10:39" else "",
    isTimerRunning = isTimerRunning,
    subTasks = listOf(
        ProjectSubTaskUi(
            projectSubTaskId = "1",
            title = "SubTask Title loasdfasdf very long, ver y",
            description = "Description SubTask 1",
            formattedDuration = formattedDuration,
            formattedStartDateTime = "23.08.2026, 10:15",
            formattedEndDateTimeUtc = null,
            isTimerRunning = false,
            isFinished = false
        ),
        ProjectSubTaskUi(
            projectSubTaskId = "2",
            title = "SubTask 2",
            description = "Description SubTask 2",
            formattedDuration = formattedDuration,
            formattedStartDateTime = "23.08.2026, 13:15",
            formattedEndDateTimeUtc = null,
            isTimerRunning = true,
            isFinished = false
        ),
        ProjectSubTaskUi(
            projectSubTaskId = "3",
            title = "SubTask 3",
            description = "Description SubTask 3",
            formattedDuration = formattedDuration,
            formattedStartDateTime = "23.08.2026, 16:30",
            formattedEndDateTimeUtc = "23.08.2026, 17:00",
            isTimerRunning = false,
            isFinished = true
        )
    ),
    isFinished = false
)

@Composable
private fun TaskItemCardPreviewContainer(content: @Composable () -> Unit) {
    TrackyTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun TaskItemCardIdlePreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 1,
            task = previewTask(),
            projectColor = PreviewProjectColor,
            isEditMode = false,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {}
        )
    }
}

@PreviewLightDark
@Composable
private fun TaskItemCardRunningPreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 2,
            task = previewTask(
                title = "Implement the subtask timer",
                formattedDuration = "00:12:44",
                isTimerRunning = true
            ),
            projectColor = PreviewProjectColor,
            isEditMode = false,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {}
        )
    }
}

@PreviewLightDark
@Composable
private fun TaskItemCardCollapsedPreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 2,
            task = previewTask(title = "Testing Tasks"),
            projectColor = PreviewProjectColor,
            isEditMode = false,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {},
            isExpanded = false
        )
    }
}

@PreviewLightDark
@Composable
private fun TaskItemCardEditModeRenamingPreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 1,
            task = previewTask(title = "Testing Tasks"),
            projectColor = PreviewProjectColor,
            isEditMode = true,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {},
            editingSubTaskId = "2",
            editSubTaskTextFieldState = TextFieldState(initialText = "Rate limits")
        )
    }
}

@PreviewLightDark
@Composable
private fun TaskItemCardCompletedPreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 3,
            task = previewTask(
                title = "Ship the color picker",
                formattedDuration = "02:05:31",
                isCompleted = true
            ),
            projectColor = PreviewProjectColor,
            isEditMode = false,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {}
        )
    }
}

@PreviewLightDark
@Composable
private fun TaskItemCardEditModePreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 4,
            task = previewTask(),
            projectColor = PreviewProjectColor,
            isEditMode = true,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {}
        )
    }
}

@PreviewLightDark
@Composable
private fun TaskItemCardEditModeRunningPreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 5,
            task = previewTask(
                title = "Implement the subtask timer",
                formattedDuration = "00:12:44",
                isTimerRunning = true
            ),
            projectColor = PreviewProjectColor,
            isEditMode = true,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {}
        )
    }
}

@Preview(name = "Long title · compact", widthDp = 320)
@Composable
private fun TaskItemCardLongTitlePreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 12,
            task = previewTask(
                title = "Quarterly planning sync with the whole product and design org"
            ),
            projectColor = PreviewProjectColor,
            isEditMode = false,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {}
        )
    }
}

@Preview(name = "Expanded width", widthDp = 840)
@Composable
private fun TaskItemCardWidePreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 1,
            task = previewTask(),
            projectColor = PreviewProjectColor,
            isEditMode = false,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {}
        )
    }
}

@Preview(name = "Font scale 2x", fontScale = 2f)
@Composable
private fun TaskItemCardLargeFontPreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 1,
            task = previewTask(),
            projectColor = PreviewProjectColor,
            isEditMode = false,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {}
        )
    }
}
