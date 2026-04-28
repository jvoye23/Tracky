package com.jvcs.tracky.features.project_tracker.presentation.project_overview.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.core.presentation.model.ProjectUi
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewAction
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewState

import kotlinx.datetime.LocalDateTime
import kotlin.time.DurationUnit

@Composable
fun ProjectCard(
    modifier: Modifier = Modifier,
    state: ProjectOverviewState,
    onAction: (ProjectOverviewAction) -> Unit,
    projectUi: ProjectUi
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(124.dp)
            .clickable {
                onAction(ProjectOverviewAction.OnProjectCardClick(projectId = projectUi.projectId!!))
            },
        shape = RoundedCornerShape(12.dp),

        elevation = CardDefaults.cardElevation(1.dp),
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),

                ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = projectUi.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = projectUi.description ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically

                ) {
                    Column() {
                        Text(
                            text = "Start",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = projectUi.startDateTimeUtc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Column() {
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = projectUi.totalDuration,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

            }

        }
    )
}


@Preview
@Composable
private fun ProjectCardPreview() {
    TrackyTheme {
        ProjectCard(
            state = ProjectOverviewState(),
            onAction = {},
            projectUi = ProjectUi(
                projectId = "1",
                title = "Project Title",
                description = "Description of the project",
                color = Color.Red,
                totalDuration = DurationUnit.HOURS.toString(),
                startDateTimeUtc = LocalDateTime(2025, 12, 1, 10, 0,0).toString(),
                isFinished = false,
                endDateTimeUtc = null,
            )
        )
    }

}