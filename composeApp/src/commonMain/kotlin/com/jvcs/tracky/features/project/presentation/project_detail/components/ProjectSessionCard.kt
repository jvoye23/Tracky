package com.jvcs.tracky.features.project.presentation.project_detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.project_detail.ProjectDetailAction
import com.jvcs.tracky.features.project.presentation.project_detail.ProjectDetailState

@Composable
fun ProjectSessionCard(
    modifier: Modifier = Modifier,
    projectTaskUi: ProjectTaskUi,
    onAction: (ProjectDetailAction) -> Unit,
    state: ProjectDetailState
) {


    ListItem(
        modifier = modifier
            .fillMaxWidth(),
        headlineContent = {
            Text(
                text = projectTaskUi.title
            )
        },
        supportingContent = {
            Text(
                text = projectTaskUi.formattedDuration
            )
        },
        trailingContent = {
            if (state.isEditMode) {
                IconButton(
                    onClick = {
                        onAction(ProjectDetailAction.OnDeleteSessionClick(projectTaskUi.projectTaskId!!))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Session",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Checkbox(
                    checked = false,
                    onCheckedChange = {

                    }
                )
            }

        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable {
                        onAction(ProjectDetailAction.OnToggleSessionTimer(projectTaskUi.projectTaskId!!))
                    }
                    .background(
                        color = if (projectTaskUi.isTimerRunning) {
                            MaterialTheme.colorScheme.errorContainer
                        } else MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ){
                Icon(
                    imageVector = if (projectTaskUi.isTimerRunning) {
                        Icons.Default.Stop
                    } else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (projectTaskUi.isTimerRunning) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },


    )


}

@Preview
@Composable
fun ProjectSessionCardPreview() {
    TrackyTheme {
        ProjectSessionCard(
            projectTaskUi = ProjectTaskUi(
                projectTaskId = "1",
                title = "This is session One",
                description = "Description One",
                formattedDuration = "00:30:59",
                formattedStateDateTime = "2023-01-01",
                formattedEndDateTimeUtc = "2023-01-01",
                isTimerRunning = false,
                subTasks = emptyList(),
                isFinished = false
            ),
            onAction = {},
            state = ProjectDetailState()
        )

    }
}