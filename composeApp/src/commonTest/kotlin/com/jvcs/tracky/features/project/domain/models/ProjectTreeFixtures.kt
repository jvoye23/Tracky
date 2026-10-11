package com.jvcs.tracky.features.project.domain.models

import kotlin.time.Instant

/** Small builders for project trees, with only the ids a test cares about. */
private val epoch = Instant.fromEpochMilliseconds(1_760_000_000_000)

fun testProject(
    id: String,
    tasks: List<ProjectTask> = emptyList(),
    title: String = "Project $id",
): Project =
    Project(
        projectId = id,
        title = title,
        description = null,
        colorArgb = null,
        totalDurationMillis = null,
        startDateTimeUtc = epoch,
        isFinished = false,
        endDateTimeUtc = null,
        projectTasks = tasks,
    )

fun testTask(
    id: String,
    projectId: String,
    intervals: List<TaskInterval> = emptyList(),
    subTasks: List<ProjectSubTask> = emptyList(),
    sortIndex: Long? = null,
): ProjectTask =
    ProjectTask(
        projectTaskId = id,
        title = "Task $id",
        description = null,
        durationMillis = null,
        startDateTimeUtc = epoch,
        parentProjectId = projectId,
        isTimerRunning = false,
        intervals = intervals,
        subTasks = subTasks,
        sortIndex = sortIndex,
    )

fun testInterval(
    id: String,
    taskId: String,
    projectId: String,
    durationMillis: Long = 60_000,
    startedByDeviceId: String? = null,
): TaskInterval =
    TaskInterval(
        intervalId = id,
        parentTaskId = taskId,
        parentProjectId = projectId,
        startDateTimeUtc = epoch,
        endDateTimeUtc = epoch,
        durationMillis = durationMillis,
        startedByDeviceId = startedByDeviceId,
    )

fun testSubTask(
    id: String,
    taskId: String,
    projectId: String,
    intervals: List<SubTaskInterval> = emptyList(),
    sortIndex: Long? = null,
): ProjectSubTask =
    ProjectSubTask(
        projectSubTaskId = id,
        parentProjectTaskId = taskId,
        parentProjectId = projectId,
        title = "Subtask $id",
        durationMillis = null,
        isTimerRunning = false,
        startDateTimeUtc = epoch,
        subTaskIntervals = intervals,
        sortIndex = sortIndex,
    )

fun testSubTaskInterval(
    id: String,
    subTaskId: String,
    taskIntervalId: String,
    projectId: String,
): SubTaskInterval =
    SubTaskInterval(
        subTaskIntervalId = id,
        parentTaskIntervalId = taskIntervalId,
        parentSubTaskId = subTaskId,
        parentProjectId = projectId,
        startDateTimeUtc = epoch,
        endDateTimeUtc = epoch,
        durationMillis = 60_000,
    )
