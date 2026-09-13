@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project.presentation.fakes

import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Domain builders shared by the daily-overview tests. Parameters are added as slices need them.
 *
 * Ids derive from the arguments rather than from a counter, so a fixture reads the same whichever
 * test built it and nothing depends on execution order.
 *
 * Unlike the file-private builders in `PerDayUiMapperTest`, a closed interval here ends at
 * `start + duration` rather than at `start`. The strip only reads `durationMillis`, so the
 * difference never mattered there; the daily overview renders "09:30 - 10:12", so it does.
 */

const val FAKE_PROJECT_ID = "project"

private val EPOCH = Instant.parse("2026-08-01T00:00:00Z")

fun project(tasks: List<ProjectTask> = emptyList()) = Project(
    projectId = FAKE_PROJECT_ID,
    title = "Project",
    description = null,
    colorArgb = null,
    totalDurationMillis = 0L,
    startDateTimeUtc = EPOCH,
    isFinished = false,
    endDateTimeUtc = null,
    projectTasks = tasks
)

fun task(
    id: String = "task-0",
    title: String = "Task",
    intervals: List<TaskInterval> = emptyList(),
    // null means "not loaded", the same distinction the domain model draws.
    subTasks: List<ProjectSubTask>? = null
) = ProjectTask(
    projectTaskId = id,
    title = title,
    description = null,
    durationMillis = 0L,
    startDateTimeUtc = EPOCH,
    parentProjectId = FAKE_PROJECT_ID,
    isTimerRunning = false,
    intervals = intervals,
    subTasks = subTasks
)

fun subTask(
    id: String = "sub-0",
    title: String = "Subtask",
    intervals: List<SubTaskInterval> = emptyList()
) = ProjectSubTask(
    projectSubTaskId = id,
    parentProjectTaskId = "task-0",
    parentProjectId = FAKE_PROJECT_ID,
    title = title,
    durationMillis = 0L,
    isTimerRunning = false,
    startDateTimeUtc = EPOCH,
    subTaskIntervals = intervals
)

private fun durationMillisOf(minutes: Long, seconds: Long) = minutes * 60_000L + seconds * 1_000L

/** A task interval starting at [start] and, unless [open], ending [minutes] + [seconds] later. */
fun interval(
    start: String,
    minutes: Long = 0L,
    seconds: Long = 0L,
    open: Boolean = false,
    id: String = "interval-$start"
) = TaskInterval(
    intervalId = id,
    parentTaskId = "task-0",
    parentProjectId = FAKE_PROJECT_ID,
    startDateTimeUtc = Instant.parse(start),
    endDateTimeUtc = if (open) null else Instant.parse(start).plus(durationMillisOf(minutes, seconds).milliseconds),
    durationMillis = if (open) 0L else durationMillisOf(minutes, seconds)
)

/** The subtask equivalent of [interval]; nests inside a task interval by construction. */
fun subInterval(
    start: String,
    minutes: Long = 0L,
    seconds: Long = 0L,
    open: Boolean = false,
    id: String = "sub-interval-$start"
) = SubTaskInterval(
    subTaskIntervalId = id,
    parentTaskIntervalId = "interval-0",
    parentSubTaskId = "sub-0",
    parentProjectId = FAKE_PROJECT_ID,
    startDateTimeUtc = Instant.parse(start),
    endDateTimeUtc = if (open) null else Instant.parse(start).plus(durationMillisOf(minutes, seconds).milliseconds),
    durationMillis = if (open) 0L else durationMillisOf(minutes, seconds)
)
