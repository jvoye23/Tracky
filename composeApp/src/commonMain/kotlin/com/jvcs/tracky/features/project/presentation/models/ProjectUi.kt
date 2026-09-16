package com.jvcs.tracky.features.project.presentation.models

import androidx.compose.ui.graphics.Color
import com.jvcs.tracky.design_system.util.formatDuration
import kotlin.time.Duration.Companion.milliseconds

data class ProjectUi(
    val projectId: String,
    val title: String,
    val description: String?,
    val color: Color?,
    /**
     * The only duration this project stores. Durations must never round-trip through their own
     * display string: the string is truncated for reading, and mapping the tree back to the domain
     * on an unrelated edit would write that truncation into the database. The string is derived
     * from this number rather than stored beside it, so the two cannot disagree.
     *
     * Deliberately not defaulted: a zero here is a real duration, not "unset", so a call site that
     * forgets it should fail to compile rather than silently store one.
     */
    val totalDurationMillis: Long,
    val startDateTimeUtc: String,
    val isFinished: Boolean,
    val useLightTextColor: Boolean = false,
    val endDateTimeUtc: String?,
    val projectTasks: List<ProjectTaskUi>? = null,
    val isPinned: Boolean = false
) {
    val totalDuration: String
        get() = formatDuration(totalDurationMillis.milliseconds)

    /**
     * displayDurationMillis, not durationMillis: a task with subtasks shows their sum, and the
     * project total has to agree with the numbers on the task rows.
     */
    val totalProjectDurationMillis: Long
        get() = projectTasks?.sumOf { it.displayDurationMillis } ?: 0L

    val totalProjectDuration: String
        get() = formatDuration(totalProjectDurationMillis.milliseconds)

    val anyTimerRunning: Boolean
        get() = projectTasks?.any { it.isTimerRunning || it.isAnySubTaskRunning } ?: false

    val allTasksDone: Boolean
        get() = !projectTasks.isNullOrEmpty() &&
                doneTaskCount == projectTasks.size

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
    /** This task's own tracked time. Required, and for the reason in [ProjectUi.totalDurationMillis]. */
    val durationMillis: Long,
    val formattedStateDateTime: String,
    val formattedEndDateTimeUtc: String,
    val isTimerRunning: Boolean,
    val subTasks: List<ProjectSubTaskUi>,
    val isFinished: Boolean
) {
    val formattedDuration: String
        get() = formatDuration(durationMillis.milliseconds)

    val doneSubTaskCount: Int
        get() = subTasks.count { it.isFinished }

    /** 0f..1f, for the progress row. A task without subtasks has no progress to show. */
    val subTaskProgress: Float
        get() = if (subTasks.isEmpty()) 0f else doneSubTaskCount.toFloat() / subTasks.size

    val totalSubTaskDurationMillis: Long
        get() = subTasks.sumOf { it.durationMillis }

    val totalSubTaskDuration: String
        get() = formatDuration(totalSubTaskDurationMillis.milliseconds)

    /**
     * What the UI shows for this task: once it has subtasks its time is theirs, summed.
     *
     * The task's own durationMillis keeps accruing in the database — a subtask opens an enclosing
     * task interval, and closing it banks the elapsed time — but that number is no longer what the
     * user sees, because timing a task with subtasks always goes through one of them.
     *
     * This ticks live for free: the ViewModel rewrites a running subtask's durationMillis from
     * TimeManager each frame, so the fold yields banked siblings + the live one.
     */
    val displayDurationMillis: Long
        get() = if (subTasks.isEmpty()) durationMillis else totalSubTaskDurationMillis

    val displayDuration: String
        get() = formatDuration(displayDurationMillis.milliseconds)

    val isAnySubTaskRunning: Boolean
        get() = subTasks.any { it.isTimerRunning }
}

data class ProjectSubTaskUi(
    val projectSubTaskId: String,
    val title: String,
    val description: String?,
    /** This subtask's tracked time. Required, and for the reason in [ProjectUi.totalDurationMillis]. */
    val durationMillis: Long,
    val formattedStartDateTime: String,
    val formattedEndDateTimeUtc: String?,
    val isTimerRunning: Boolean,
    val isFinished: Boolean
) {
    val formattedDuration: String
        get() = formatDuration(durationMillis.milliseconds)
}