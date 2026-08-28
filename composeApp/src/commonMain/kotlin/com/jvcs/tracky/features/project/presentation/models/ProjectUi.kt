package com.jvcs.tracky.features.project.presentation.models

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
    val projectTasks: List<ProjectTaskUi>? = null,
    val isPinned: Boolean = false
) {
    val totalProjectDuration: String
        get() {
            // 2. Sum up the durations.
            // We use 'fold' starting at ZERO to safely handle the list iteration.
            // displayDuration, not formattedDuration: a task with subtasks shows their sum, and the
            // project total has to agree with the numbers on the task rows.
            val total = projectTasks?.fold(Duration.ZERO) { acc, session ->
                acc + parseDuration(session.displayDuration)
            } ?: Duration.ZERO

            // 3. Format the total Duration back to String
            return formatDuration(total)
        }

    val anyTimerRunning: Boolean
        get() = projectTasks?.any { it.isTimerRunning || it.isAnySubTaskRunning } ?: false

    val allTasksDone: Boolean
        get() = !projectTasks.isNullOrEmpty() &&
                projectTasks.all { it.formattedEndDateTimeUtc.isNotBlank() }

    val doneTaskCount: Int
        get() = projectTasks?.count { it.isFinished } ?: 0

    /** 0f..1f, for the task progress row. A project without tasks has no progress to show. */
    val taskProgress: Float
        get() = if (projectTasks.isNullOrEmpty()) 0f else doneTaskCount.toFloat() / projectTasks.size
}

data class ProjectTaskUi(
    val projectTaskId: String,
    val title: String,
    val description: String?,
    val formattedDuration: String,
    val formattedStateDateTime: String,
    val formattedEndDateTimeUtc: String,
    val isTimerRunning: Boolean,
    val subTasks: List<ProjectSubTaskUi>,
    val isFinished: Boolean
) {
    val doneSubTaskCount: Int
        get() = subTasks.count { it.isFinished }

    /** 0f..1f, for the progress row. A task without subtasks has no progress to show. */
    val subTaskProgress: Float
        get() = if (subTasks.isEmpty()) 0f else doneSubTaskCount.toFloat() / subTasks.size

    val totalSubTaskDuration: String
        get() = formatDuration(
            subTasks.fold(Duration.ZERO) { acc, subTask ->
                acc + parseDuration(subTask.formattedDuration)
            }
        )

    /**
     * What the UI shows for this task: once it has subtasks its time is theirs, summed.
     *
     * The task's own durationMillis keeps accruing in the database — a subtask opens an enclosing
     * task interval, and closing it banks the elapsed time — but that number is no longer what the
     * user sees, because timing a task with subtasks always goes through one of them.
     *
     * This ticks live for free: the ViewModel rewrites a running subtask's formattedDuration from
     * TimeManager each frame, so the fold yields banked siblings + the live one.
     */
    val displayDuration: String
        get() = if (subTasks.isEmpty()) formattedDuration else totalSubTaskDuration

    val isAnySubTaskRunning: Boolean
        get() = subTasks.any { it.isTimerRunning }
}

data class ProjectSubTaskUi(
    val projectSubTaskId: String,
    val title: String,
    val description: String?,
    val formattedDuration: String,
    val formattedStartDateTime: String,
    val formattedEndDateTimeUtc: String?,
    val isTimerRunning: Boolean,
    val isFinished: Boolean
)