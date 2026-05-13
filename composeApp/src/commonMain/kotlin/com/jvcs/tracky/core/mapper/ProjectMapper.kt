package com.jvcs.tracky.core.mapper

import com.jvcs.tracky.core.data.networking.CreateProjectRequest
import com.jvcs.tracky.core.data.networking.CreateProjectTaskRequest
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTasks
import com.jvcs.tracky.core.database.relation.TaskWithIntervals
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval

fun Project.toProjectEntity(): ProjectEntity {
    return ProjectEntity(
        projectId = projectId,
        title = title,
        description = description,
        color = colorArgb,
        totalDuration = totalDurationMillis,
        startDateTimeUtc = startDateTimeUtc,
        isFinished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeUtc,
    )
}

fun ProjectEntity.toProject(): Project {
    return Project(
        projectId = projectId,
        title = title,
        description = description,
        colorArgb = color,
        totalDurationMillis = totalDuration,
        startDateTimeUtc = startDateTimeUtc,
        isFinished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeUtc,
    )
}

fun ProjectWithTasks.toProject(): Project {
    return Project(
        projectId = project.projectId,
        title = project.title,
        description = project.description,
        colorArgb = project.color,
        totalDurationMillis = project.totalDuration,
        startDateTimeUtc = project.startDateTimeUtc,
        isFinished = project.isFinished,
        useLightTextColor = project.useLightTextColor,
        endDateTimeUtc = project.endDateTimeUtc,
        projectTasks = projectTasks.map { it.toProjectSession() }
    )
}

fun ProjectTaskEntity.toProjectSession(): ProjectTask {
    return ProjectTask(
        projectTaskId = recordId,
        title = description,
        durationMillis = durationMillis,
        startDateTimeUtc = startDateTimeUtc,
        endDateTimeUtc = endDateTimeUtc,
        isFinished = isFinished,
        parentProjectId = parentProjectId,
        isTimerRunning = isTimerRunning
    )
}

fun TaskWithIntervals.toProjectSession(): ProjectTask {
    return ProjectTask(
        projectTaskId = task.recordId,
        title = task.description,
        durationMillis = task.durationMillis,
        startDateTimeUtc = task.startDateTimeUtc,
        endDateTimeUtc = task.endDateTimeUtc,
        isFinished = task.isFinished,
        parentProjectId = task.parentProjectId,
        isTimerRunning = task.isTimerRunning,
        intervals = intervals.map { it.toSessionInterval() }
    )
}

fun ProjectTask.toProjectSessionEntity(): ProjectTaskEntity {
    return ProjectTaskEntity(
        recordId = projectTaskId,
        parentProjectId = parentProjectId,
        description = title,
        durationMillis = durationMillis ?: 0L,
        startDateTimeUtc = startDateTimeUtc,
        endDateTimeUtc = endDateTimeUtc,
        isFinished = isFinished,
        isTimerRunning = isTimerRunning
    )
}

fun TaskIntervalEntity.toSessionInterval(): TaskInterval {
    return TaskInterval(
        intervalId = intervalId,
        parentSessionId = parentTaskId,
        startDateTimeUtc = startDateTimeUtc,
        endDateTimeUtc = endDateTimeUtc,
        durationMillis = durationMillis
    )
}

fun TaskInterval.toSessionIntervalEntity(): TaskIntervalEntity {
    return TaskIntervalEntity(
        intervalId = intervalId,
        parentTaskId = parentSessionId,
        startDateTimeUtc = startDateTimeUtc,
        endDateTimeUtc = endDateTimeUtc,
        durationMillis = durationMillis
    )
}

fun Project.toCreateProjectRequest(): CreateProjectRequest {
    return CreateProjectRequest(
        id = projectId,
        title = title,
        description = description ?: "",
        color = colorArgb ?: 0,
        startDateTimeUtc = startDateTimeUtc,
        useLightTextColor = useLightTextColor
    )
}

fun ProjectTask.toCreateProjectTaskRequest(): CreateProjectTaskRequest {
    return CreateProjectTaskRequest(
        id = projectTaskId,
        durationMillis = durationMillis ?: 0,
        startDateTimeUtc = startDateTimeUtc,
        endDateTimeUtc = endDateTimeUtc,
        intervals = intervals,
        finished = isFinished,
        timerRunning = isTimerRunning
    )
}
