package com.jvcs.tracky.core.presentation.model

import androidx.compose.ui.graphics.Color
import com.jvcs.tracky.design_system.util.formatDuration
import com.jvcs.tracky.design_system.util.parseDuration
import kotlin.time.Duration

data class ProjectUi(
    val projectId: String,
    val title: String,
    val description: String?,
    val color: Color?,
    val totalDuration: String,
    val startDateTimeUtc: String,
    val isFinished: Boolean,
    val useLightTextColor: Boolean = false,
    val endDateTimeUtc: String?,
    val projectTasks: List<ProjectTaskUi>? = null
) {
    val totalProjectDuration: String
        get() {
            // 2. Sum up the durations.
            // We use 'fold' starting at ZERO to safely handle the list iteration.
            val total = projectTasks?.fold(Duration.ZERO) { acc, session ->
                acc + parseDuration(session.formattedDuration)
            } ?: Duration.ZERO

            // 3. Format the total Duration back to String
            return formatDuration(total)
        }

    val anyTimerRunning: Boolean
        get() = projectTasks?.any { it.isTimerRunning } ?: false

    val allTasksDone: Boolean
        get() = !projectTasks.isNullOrEmpty() &&
                projectTasks.all { it.formattedEndDateTimeUtc.isNotBlank() }
}

data class ProjectTaskUi(
    val id: String?,
    val title: String,
    val formattedDuration: String,
    val formattedStateDateTime: String,
    val formattedEndDateTimeUtc: String,
    val isTimerRunning: Boolean
)