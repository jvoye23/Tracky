package com.jvcs.tracky.core.presentation.model

import androidx.compose.ui.graphics.Color
import com.jvcs.tracky.design_system.util.formatDuration
import com.jvcs.tracky.design_system.util.parseDuration
import kotlin.time.Duration

data class ProjectUi(
    val projectId: String?, // null when creating a new project
    val title: String,
    val description: String?,
    val color: Color?,
    val totalDuration: String,
    val startDateTimeUtc: String,
    val isFinished: Boolean,
    val endDateTimeUtc: String?,
    val projectSessions: List<ProjectSessionUi>? = null
) {
    val totalProjectDuration: String
        get() {
            // 2. Sum up the durations.
            // We use 'fold' starting at ZERO to safely handle the list iteration.
            val total = projectSessions?.fold(Duration.ZERO) { acc, session ->
                acc + parseDuration(session.formattedDuration)
            } ?: Duration.ZERO

            // 3. Format the total Duration back to String
            return formatDuration(total)
        }
}

data class ProjectSessionUi(
    val id: String?,
    val title: String,
    val formattedDuration: String,
    val formattedStateDateTime: String,
    val formattedEndDateTimeUtc: String,
    val isTimerRunning: Boolean
)