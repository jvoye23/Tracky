package com.jvcs.tracky.features.project_tracker.presentation.project_detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
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
import com.jvcs.tracky.core.presentation.model.ProjectSessionUi
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project_tracker.presentation.project_detail.ProjectDetailAction
import com.jvcs.tracky.features.project_tracker.presentation.project_detail.ProjectDetailState

@Composable
fun ProjectSessionCard(
    modifier: Modifier = Modifier,
    projectSessionUi: ProjectSessionUi,
    onAction: (ProjectDetailAction) -> Unit,
    state: ProjectDetailState
) {


    ListItem(
        modifier = modifier
            .fillMaxWidth(),
        headlineContent = {
            Text(
                text = projectSessionUi.title
            )
        },
        supportingContent = {
            Text(
                text = projectSessionUi.formattedDuration
            )
        },
        trailingContent = {
            if (state.isEditMode) {
                IconButton(
                    onClick = {
                        onAction(ProjectDetailAction.OnDeleteSessionClick(projectSessionUi.id!!))
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
                        onAction(ProjectDetailAction.OnToggleSessionTimer(projectSessionUi.id!!))
                    }
                    .background(
                        color = if (projectSessionUi.isTimerRunning) {
                            MaterialTheme.colorScheme.errorContainer
                        } else MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ){
                Icon(
                    imageVector = if (projectSessionUi.isTimerRunning) {
                        Icons.Default.Stop
                    } else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (projectSessionUi.isTimerRunning) {
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
            projectSessionUi = ProjectSessionUi(
                id = "1",
                title = "This is session One",
                formattedDuration = "00:30:59",
                formattedStateDateTime = "2023-01-01",
                formattedEndDateTimeUtc = "2023-01-01",
                isTimerRunning = false
            ),
            onAction = {},
            state = ProjectDetailState()
        )

    }
}