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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jvcs.tracky.design_system.Icon_ChevronDown
import com.jvcs.tracky.design_system.Icon_ChevronUp
import com.jvcs.tracky.design_system.Icon_Plus
import com.jvcs.tracky.design_system.Icon_Timer
import com.jvcs.tracky.design_system.Icon_Trash
import com.jvcs.tracky.design_system.components.TrackyCheckbox
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.models.ProjectSubTaskUi
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.features.project.presentation.util.rememberSubTaskDragDropState
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.add_subtask
import tracky.composeapp.generated.resources.delete
import tracky.composeapp.generated.resources.hide_subtasks
import tracky.composeapp.generated.resources.reorder
import tracky.composeapp.generated.resources.show_subtasks
import tracky.composeapp.generated.resources.start_timer
import tracky.composeapp.generated.resources.stop_timer
import tracky.composeapp.generated.resources.subtask_progress

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
        label = "alpha",
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
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier =
            modifier
                .size(buttonSize)
                .graphicsLayer {
                    alpha = if (isTimerRunning) pulseAlpha() else 1f
                },
        enabled = !isFinished,
        colors =
            IconButtonDefaults.filledTonalIconButtonColors(
                contentColor = if (isTimerRunning) Color.White else projectColor,
                containerColor = if (isTimerRunning) projectColor else projectColor.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.outline,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        shape = if (isTimerRunning) RoundedCornerShape(10.dp) else CircleShape,
    ) {
        Icon(
            imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription =
                stringResource(
                    if (isTimerRunning) Res.string.stop_timer else Res.string.start_timer,
                ),
            modifier = Modifier.size(buttonSize * 0.45f),
        )
    }
}

/**
 * The reorder grip shown in place of the timer button in edit mode.
 *
 * The drag starts on the grip itself rather than on a long-press of the row, which is what the grip
 * affordance promises and what keeps the rest of the card tappable while edit mode is on — a
 * subtask title still renames, the trash button still deletes.
 *
 * [isReorderable] gates both the gesture and the accessibility label: the draft "+ Subtask" row
 * draws a handle too, and that subtask does not exist yet, so announcing "Reorder" there would
 * promise an affordance that is not (and cannot be) wired.
 *
 * The drawn icon stays 20.dp per the design while the touch target is padded out to [TouchTarget],
 * because a bare 20.dp Icon is well under the minimum a finger can reliably hit.
 */
@Composable
private fun DragHandle(
    modifier: Modifier = Modifier,
    isReorderable: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (dragAmountY: Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .size(TouchTarget)
                .then(
                    if (isReorderable) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { change, dragAmount ->
                                    // Consumed so the LazyColumn underneath does not scroll with the
                                    // finger while a row is being dragged.
                                    change.consume()
                                    onDrag(dragAmount.y)
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragCancel() },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = if (isReorderable) stringResource(Res.string.reorder) else null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Minimum comfortable touch target; the grip is drawn smaller but must be grabbable. */
private val TouchTarget = 48.dp

/** Dashed "+ Subtask" pill closing out the edit-mode card. */
@Composable
private fun AddSubTaskPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(16.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icon_Plus,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(Res.string.add_subtask),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Trash icon on a tinted square, per the edit-mode design. */
@Composable
private fun DeleteTaskButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp),
        colors =
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.error,
            ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Icon(
            imageVector = Icon_Trash,
            contentDescription = stringResource(Res.string.delete),
            modifier = Modifier.size(16.dp),
        )
    }
}

private val TaskCardShape = RoundedCornerShape(12.dp)

/**
 * The card's outline, shared with the rows edit mode outlines inside it so they cannot drift apart:
 * a hairline normally, and a project-tinted 1dp stroke while that item's timer runs.
 */
@Composable
private fun taskBorder(isTimerRunning: Boolean, projectColor: Color): BorderStroke =
    BorderStroke(
        if (isTimerRunning) 1.dp else 0.dp,
        if (isTimerRunning) projectColor.copy(0.2f) else MaterialTheme.colorScheme.outlineVariant,
    )

/** Edit mode outlines each tappable row - the task and every subtask - the way the card is. */
@Composable
private fun Modifier.editModeRowBorder(
    isEditMode: Boolean,
    isTimerRunning: Boolean,
    projectColor: Color,
): Modifier = if (isEditMode) border(taskBorder(isTimerRunning, projectColor), TaskCardShape) else this

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
    // Edit-mode taps that open the edit-text screen for the task, one of its subtasks, or a new one.
    onTaskTitleClick: () -> Unit = {},
    onSubTaskClick: (subTaskId: String) -> Unit = {},
    onAddSubTaskClick: () -> Unit = {},
    // Reorder of this whole card among its siblings, driven from the grip on the task row. Off by
    // default so every preview and any other caller keeps a purely decorative handle.
    isReorderable: Boolean = false,
    onReorderDragStart: () -> Unit = {},
    onReorderDrag: (dragAmountY: Float) -> Unit = {},
    onReorderDragEnd: () -> Unit = {},
    onReorderDragCancel: () -> Unit = {},
    // Reorder of this card's subtasks among themselves, driven from the grip on each subtask row.
    onSubTaskReorderMove: (fromSubTaskId: String, toSubTaskId: String) -> Unit = { _, _ -> },
    onSubTaskReorderCommit: () -> Unit = {},
    onSubTaskReorderCancel: () -> Unit = {},
) {
    // One drag state per card. Rows report their measured bounds into it, because a plain Column
    // has no layoutInfo for a reorder to hit-test against.
    val subTaskDragState =
        rememberSubTaskDragDropState(
            taskId = task.projectTaskId,
            onMove = onSubTaskReorderMove,
        )

    val isPulsing = task.isTimerRunning || task.subTasks.any { it.isTimerRunning }
    val pulseAlpha by rememberPulseAlpha(enabled = isPulsing)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                // Edit mode rearranges and renames the card's contents; opening the task's detail
                // screen from there would be a surprise, so only the view-mode card is a link.
                .then(if (isEditMode) Modifier else Modifier.clickable { onCardClick() }),
        shape = TaskCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = taskBorder(task.isTimerRunning, projectColor),
        shadowElevation = if (task.isTimerRunning) 4.dp else 2.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Main Task
            if (isEditMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,

                ) {
                    // In edit mode the grip moves the whole card, so it sits outside the outline that
                    // wraps the title - unlike a subtask's grip, which moves only its own row.
                    DragHandle(
                        isReorderable = isReorderable,
                        onDragStart = onReorderDragStart,
                        onDrag = onReorderDrag,
                        onDragEnd = onReorderDragEnd,
                        onDragCancel = onReorderDragCancel,
                    )
                    DeleteTaskButton(onClick = onDeleteClick)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val textDecoration = if (task.isFinished) TextDecoration.LineThrough else null
                val contentAlpha = if (task.isFinished) 0.4f else 1f

                if (!isEditMode) {
                    TimerToggleButton(
                        isTimerRunning = task.isTimerRunning,
                        isFinished = task.isFinished,
                        projectColor = projectColor,
                        pulseAlpha = { pulseAlpha },
                        buttonSize = 48.dp,
                        onClick = onToggleTimer,
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .editModeRowBorder(isEditMode, task.isTimerRunning, projectColor)
                            .then(if (isEditMode) Modifier.padding(8.dp) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The design drops the ordinal in edit mode, where the row is handle + title only.
                            if (!isEditMode) {
                                Text(
                                    text = index.toString().padStart(2, '0'),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textDecoration = textDecoration,
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .then(
                                            if (isEditMode) {
                                                Modifier
                                                    .padding(12.dp)
                                                    .clickable { onTaskTitleClick() }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textDecoration = textDecoration,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!isEditMode) {
                            Text(
                                // Subtask sum once there are subtasks; its own time otherwise.
                                text = task.displayDuration,
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color =
                                    if (task.isTimerRunning) {
                                        projectColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                                    },
                                letterSpacing = (-0.5).sp,
                            )
                        }
                    }
                    if (!isEditMode) {
                        TrackyCheckbox(
                            checked = task.isFinished,
                            onCheckedChange = { onCheckedChange() },
                        )
                    }
                }
            }

            // Progress over the subtasks, with the chevron that collapses them. Edit mode drops
            // the whole row — the design shows only handles, titles and delete buttons there.
            if (task.subTasks.isNotEmpty() && !isEditMode) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LinearProgressIndicator(
                            // Lambda overload: the progress is read in the draw phase, so animating it
                            // later will not recompose the card.
                            progress = { task.subTaskProgress },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(6.dp),
                            color = projectColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                        Text(
                            text =
                                stringResource(
                                    Res.string.subtask_progress,
                                    task.doneSubTaskCount,
                                    task.subTasks.size,
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                if (isExpanded) {
                                    stringResource(Res.string.hide_subtasks)
                                } else {
                                    stringResource(Res.string.show_subtasks)
                                },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        IconButton(
                            onClick = onToggleExpanded,
                            modifier = Modifier,
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icon_ChevronUp else Icon_ChevronDown,
                                contentDescription =
                                    stringResource(
                                        if (isExpanded) Res.string.hide_subtasks else Res.string.show_subtasks,
                                    ),
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // optional SubTasks — the design groups them on a tinted band rather than
            // separating them from the main row with a divider.
            // Edit mode always shows them: there is no progress row there to expand them again.
            if (task.subTasks.isNotEmpty() && (isExpanded || isEditMode)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(start = 40.dp),
                    // Keeps the edit-mode outlines of neighbouring subtasks from touching.
                    verticalArrangement = if (isEditMode) Arrangement.spacedBy(8.dp) else Arrangement.Top,
                ) {
                    task.subTasks.forEachIndexed { subTaskIndex, projectSubTaskUi ->
                        val subTaskId = projectSubTaskUi.projectSubTaskId

                        // Keyed so a row's identity follows the subtask rather than the position,
                        // which is what lets the bounds it reports survive a reorder.
                        key(subTaskId) {
                            DisposableEffect(subTaskId) {
                                onDispose { subTaskDragState.onRowDisposed(subTaskId) }
                            }
                            val isDragActive =
                                subTaskId == subTaskDragState.draggingItemKey ||
                                    subTaskId == subTaskDragState.settlingItemKey
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coordinates ->
                                            subTaskDragState.onRowPlaced(
                                                key = subTaskId,
                                                top = coordinates.positionInParent().y,
                                                height = coordinates.size.height.toFloat(),
                                            )
                                        }.then(
                                            if (isDragActive) {
                                                Modifier
                                                    .zIndex(1f)
                                                    .graphicsLayer {
                                                        translationY =
                                                            if (subTaskId == subTaskDragState.draggingItemKey) {
                                                                subTaskDragState.draggingItemOffset
                                                            } else {
                                                                subTaskDragState.settlingItemOffset
                                                            }
                                                    }
                                            } else {
                                                Modifier
                                            },
                                        )
                                        // After the drag layer, so the outline travels with a dragged row.
                                        .editModeRowBorder(isEditMode, projectSubTaskUi.isTimerRunning, projectColor)
                                        .padding(vertical = 6.dp)
                                        .then(if (isEditMode) Modifier.padding(horizontal = 8.dp) else Modifier),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                val textDecoration =
                                    if (projectSubTaskUi.isFinished) TextDecoration.LineThrough else null
                                val contentAlpha = if (projectSubTaskUi.isFinished) 0.4f else 1f

                                if (isEditMode) {
                                    DragHandle(
                                        isReorderable = isReorderable,
                                        onDragStart = { subTaskDragState.onDragStart(subTaskId) },
                                        onDrag = { dragAmountY -> subTaskDragState.onDrag(dragAmountY) },
                                        onDragEnd = {
                                            if (subTaskDragState.hasMoved) onSubTaskReorderCommit()
                                            subTaskDragState.onDragEnd()
                                        },
                                        onDragCancel = {
                                            onSubTaskReorderCancel()
                                            subTaskDragState.onDragCancel()
                                        },
                                    )
                                } else {
                                    TimerToggleButton(
                                        isTimerRunning = projectSubTaskUi.isTimerRunning,
                                        isFinished = projectSubTaskUi.isFinished,
                                        projectColor = projectColor,
                                        pulseAlpha = { pulseAlpha },
                                        buttonSize = 48.dp,
                                        onClick = { onToggleSubTaskTimer(projectSubTaskUi.projectSubTaskId) },
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "$index.${subTaskIndex + 1}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                                            textDecoration = textDecoration,
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .then(
                                                        if (isEditMode) {
                                                            Modifier.clickable { onSubTaskClick(subTaskId) }
                                                        } else {
                                                            Modifier
                                                        },
                                                    ),
                                            text = projectSubTaskUi.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                                            textDecoration = textDecoration,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (!isEditMode) {
                                        Text(
                                            text = projectSubTaskUi.formattedDuration,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color =
                                                if (projectSubTaskUi.isTimerRunning) {
                                                    projectColor
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                                                },
                                            letterSpacing = (-0.5).sp,
                                        )
                                    }
                                }
                                if (isEditMode) {
                                    DeleteTaskButton(
                                        onClick = { onDeleteSubTaskClick(projectSubTaskUi.projectSubTaskId) },
                                    )
                                } else {
                                    TrackyCheckbox(
                                        checked = projectSubTaskUi.isFinished,
                                        onCheckedChange = {
                                            onSubTaskCheckedChange(projectSubTaskUi.projectSubTaskId)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (isEditMode) {
                    AddSubTaskPill(
                        onClick = onAddSubTaskClick,
                        modifier = Modifier.padding(start = 40.dp, top = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }
}

private val PreviewProjectColor = Color(0xFF475D92)

private fun previewTask(
    title: String = "Wireframe the settings screen",
    description: String = "Task Description",
    durationMillis: Long = 5_047_000L,
    isTimerRunning: Boolean = false,
    isCompleted: Boolean = false,
) = ProjectTaskUi(
    projectTaskId = "task-1",
    title = title,
    description = description,
    durationMillis = durationMillis,
    formattedStateDateTime = "23.08.2026, 09:15",
    formattedEndDateTimeUtc = if (isCompleted) "23.08.2026, 10:39" else "",
    isTimerRunning = isTimerRunning,
    subTasks =
        listOf(
            ProjectSubTaskUi(
                projectSubTaskId = "1",
                title = "SubTask Title loasdfasdf very long, ver y",
                description = "Description SubTask 1",
                durationMillis = durationMillis,
                formattedStartDateTime = "23.08.2026, 10:15",
                formattedEndDateTimeUtc = null,
                isTimerRunning = false,
                isFinished = false,
            ),
            ProjectSubTaskUi(
                projectSubTaskId = "2",
                title = "SubTask 2",
                description = "Description SubTask 2",
                durationMillis = durationMillis,
                formattedStartDateTime = "23.08.2026, 13:15",
                formattedEndDateTimeUtc = null,
                isTimerRunning = true,
                isFinished = false,
            ),
            ProjectSubTaskUi(
                projectSubTaskId = "3",
                title = "SubTask 3",
                description = "Description SubTask 3",
                durationMillis = durationMillis,
                formattedStartDateTime = "23.08.2026, 16:30",
                formattedEndDateTimeUtc = "23.08.2026, 17:00",
                isTimerRunning = false,
                isFinished = true,
            ),
        ),
    isFinished = false,
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
            onSubTaskCheckedChange = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun TaskItemCardRunningPreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 2,
            task =
                previewTask(
                    title = "Implement the subtask timer",
                    durationMillis = 764_000L,
                    isTimerRunning = true,
                ),
            projectColor = PreviewProjectColor,
            isEditMode = false,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {},
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
            isExpanded = false,
        )
    }
}

@PreviewLightDark
@Composable
private fun TaskItemCardCompletedPreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 3,
            task =
                previewTask(
                    title = "Ship the color picker",
                    durationMillis = 7_531_000L,
                    isCompleted = true,
                ),
            projectColor = PreviewProjectColor,
            isEditMode = false,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {},
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
            onSubTaskCheckedChange = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun TaskItemCardEditModeRunningPreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 5,
            task =
                previewTask(
                    title = "Implement the subtask timer",
                    durationMillis = 764_000L,
                    isTimerRunning = true,
                ),
            projectColor = PreviewProjectColor,
            isEditMode = true,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {},
        )
    }
}

@Preview(name = "Long title · compact", widthDp = 320)
@Composable
private fun TaskItemCardLongTitlePreview() {
    TaskItemCardPreviewContainer {
        TaskItemCard(
            index = 12,
            task =
                previewTask(
                    title = "Quarterly planning sync with the whole product and design org",
                ),
            projectColor = PreviewProjectColor,
            isEditMode = false,
            onToggleTimer = {},
            onDeleteClick = {},
            onCardClick = {},
            onCheckedChange = {},
            onToggleSubTaskTimer = {},
            onDeleteSubTaskClick = {},
            onSubTaskCheckedChange = {},
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
            onSubTaskCheckedChange = {},
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
            onSubTaskCheckedChange = {},
        )
    }
}
