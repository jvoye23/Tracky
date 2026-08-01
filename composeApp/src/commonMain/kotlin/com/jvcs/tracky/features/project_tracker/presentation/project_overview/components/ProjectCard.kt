package com.jvcs.tracky.features.project_tracker.presentation.project_overview.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvcs.tracky.core.presentation.model.ProjectTaskUi
import com.jvcs.tracky.core.presentation.model.ProjectUi
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.theme.timerStyle
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.done
import tracky.composeapp.generated.resources.duration
import tracky.composeapp.generated.resources.in_progress
import tracky.composeapp.generated.resources.start_date
import kotlin.time.DurationUnit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(
    modifier: Modifier = Modifier,
    projectUi: ProjectUi,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onToggleSelection: () -> Unit = {},
    isEditModeActive: Boolean = false,
    isSelected: Boolean = false,
    isReorderable: Boolean = false,
    onReorderDragStart: () -> Unit = {},
    onReorderDrag: (dragAmountY: Float) -> Unit = {},
    onReorderDragEnd: () -> Unit = {},
    onReorderDragCancel: () -> Unit = {}
) {
    val contentColor = if (projectUi.useLightTextColor) Color.White else Color.Black
    val cardShape = CardDefaults.elevatedShape
    val selectionBorder = if (isSelected) {
        Modifier.border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = cardShape
        )
    } else {
        Modifier
    }

    ElevatedCard(
        modifier = modifier
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = {
                    if (isEditModeActive) {
                        onToggleSelection()
                    } else {
                        onClick()
                    }
                }
            )
            // Long-press also arms a reorder drag (Custom sort only). onLongClick above still enters
            // edit mode; the drag's first movement clears it (onReorderDrag). A long-press without
            // movement leaves the card in edit mode, exactly like before.
            .then(
                if (isReorderable) {
                    Modifier.pointerInput(projectUi.projectId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onReorderDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onReorderDrag(dragAmount.y)
                            },
                            onDragEnd = { onReorderDragEnd() },
                            onDragCancel = { onReorderDragCancel() }
                        )
                    }
                } else Modifier
            )
            .then(selectionBorder),
        colors = CardDefaults.elevatedCardColors(
            containerColor = projectUi.color ?: MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // HEADER: Title & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isEditModeActive) {
                    Checkbox(
                        checked = isSelected ,
                        onCheckedChange = { onToggleSelection() },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = projectUi.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (projectUi.anyTimerRunning || projectUi.allTasksDone) {
                    Spacer(modifier = Modifier.width(12.dp))

                    ProjectStatusBadge(
                        isActive = projectUi.anyTimerRunning,
                        useLightTextColor = projectUi.useLightTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // BODY: Description
            Text(
                text = projectUi.description ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(20.dp))

            // FOOTER: Stacked Meta Information
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                MetaInfoStackedItem(
                    label = stringResource(Res.string.start_date),
                    icon = Icons.Rounded.CalendarToday,
                    value = projectUi.startDateTimeUtc,
                    contentColor = contentColor
                )
                MetaInfoStackedItem(
                    label = stringResource(Res.string.duration),
                    icon = Icons.Rounded.Timer,
                    value = projectUi.totalProjectDuration,
                    contentColor = contentColor
                )
            }
        }
    }
}

@Composable
private fun ProjectStatusBadge(isActive: Boolean, useLightTextColor: Boolean) {
    val containerColor = if (isActive) {
        if (useLightTextColor) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
    } else {
        if (useLightTextColor) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (useLightTextColor) Color.White else {
        if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = if (isActive) Icons.Rounded.PlayArrow else Icons.Rounded.Check
    val label = if (isActive) stringResource(Res.string.in_progress) else stringResource(Res.string.done)

    Row(
        modifier = Modifier
            .background(color = containerColor, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = contentColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

@Composable
private fun MetaInfoStackedItem(
    label: String,
    icon: ImageVector,
    value: String,
    contentColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Top Label (e.g., "START DATE")
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp // Adds the tracking-wider look from the design
            ),
            color = contentColor.copy(alpha = 0.7f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Text(
                text = value,
                style = MaterialTheme.typography.timerStyle.copy(
                    fontSize = 15.sp
                ),
                color = contentColor
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProjectCardPreview() {
    TrackyTheme  {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Timer running -> "In Progress" badge
                ProjectCard(
                    projectUi = ProjectUi(
                        projectId = "1",
                        title = "Running Project",
                        description = "Description of the project",
                        color = Color.Cyan,
                        totalDuration = DurationUnit.HOURS.toString(),
                        startDateTimeUtc = LocalDateTime(2025, 12, 1, 10, 0,0).toString(),
                        isFinished = false,
                        endDateTimeUtc = null,
                        projectTasks = listOf(
                            ProjectTaskUi(
                                id = "t1",
                                title = "Task 1",
                                formattedDuration = "1h 0m",
                                formattedStateDateTime = "10:00",
                                formattedEndDateTimeUtc = "",
                                isTimerRunning = true
                            )
                        )
                    )
                )
                // All tasks done -> "Done" badge
                ProjectCard(
                    projectUi = ProjectUi(
                        projectId = "2",
                        title = "Completed Project",
                        description = "Description of the project",
                        color = Color.Blue,
                        totalDuration = DurationUnit.HOURS.toString(),
                        startDateTimeUtc = LocalDateTime(2025, 12, 1, 10, 0,0).toString(),
                        isFinished = true,
                        endDateTimeUtc = null,
                        projectTasks = listOf(
                            ProjectTaskUi(
                                id = "t1",
                                title = "Task 1",
                                formattedDuration = "1h 0m",
                                formattedStateDateTime = "10:00",
                                formattedEndDateTimeUtc = "11:00",
                                isTimerRunning = false
                            )
                        )
                    )
                )
                // Neither running nor all done -> no badge
                ProjectCard(
                    projectUi = ProjectUi(
                        projectId = "3",
                        title = "Idle Project",
                        description = "Description of the project",
                        color = Color.Yellow,
                        totalDuration = DurationUnit.HOURS.toString(),
                        startDateTimeUtc = LocalDateTime(2025, 12, 1, 10, 0,0).toString(),
                        isFinished = false,
                        endDateTimeUtc = null,
                        projectTasks = listOf(
                            ProjectTaskUi(
                                id = "t1",
                                title = "Task 1",
                                formattedDuration = "1h 0m",
                                formattedStateDateTime = "10:00",
                                formattedEndDateTimeUtc = "",
                                isTimerRunning = false
                            )
                        ),
                        useLightTextColor = false
                    ),
                    isSelected = false,
                    isEditModeActive = true
                )
                // No tasks -> no badge
                ProjectCard(
                    projectUi = ProjectUi(
                        projectId = "4",
                        title = "Empty Project",
                        description = "Description of the project",
                        color = Color.Magenta,
                        totalDuration = DurationUnit.HOURS.toString(),
                        startDateTimeUtc = LocalDateTime(2025, 12, 1, 10, 0,0).toString(),
                        isFinished = false,
                        endDateTimeUtc = null,
                        useLightTextColor = true
                    )
                )
            }
        }
    }
}