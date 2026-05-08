package com.jvcs.tracky.core.mapper

import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTasks
import com.jvcs.tracky.core.database.relation.TaskWithIntervals
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun Project.toProjectEntity(): ProjectEntity {
    return ProjectEntity(
        projectId = projectId,
        title = title,
        description = description,
        color = colorArgb,
        totalDuration = totalDurationMillis,
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        isFinished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
    )
}

@OptIn(ExperimentalTime::class)
fun ProjectEntity.toProject(): Project {
    return Project(
        projectId = projectId,
        title = title,
        description = description,
        colorArgb = color,
        totalDurationMillis = totalDuration,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        isFinished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = if (endDateTimeEpochMs == null) null else
            Instant.fromEpochMilliseconds(endDateTimeEpochMs),
        //projectRecords = projectRecords
    )
}

@OptIn(ExperimentalTime::class)
fun ProjectWithTasks.toProject(): Project {
    return Project(
        projectId = project.projectId,
        title = project.title,
        description = project.description,
        colorArgb = project.color,
        totalDurationMillis = project.totalDuration,
        startDateTimeUtc = Instant.fromEpochMilliseconds(project.startDateTimeEpochMs),
        isFinished = project.isFinished,
        useLightTextColor = project.useLightTextColor,
        endDateTimeUtc = if (project.endDateTimeEpochMs == null) null else
            Instant.fromEpochMilliseconds(project.endDateTimeEpochMs),
        projectTasks = projectTasks.map { it.toProjectSession() }
    )
}

@OptIn(ExperimentalTime::class)
fun ProjectTaskEntity.toProjectSession(): ProjectTask {
    return ProjectTask(
        projectTaskId = recordId,
        title = description,
        durationMillis = durationMillis,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        endDateTimeUtc = endDateTimeEpochMs?.let { Instant.fromEpochMilliseconds(it) },
        isFinished = isFinished,
        parentProjectId = parentProjectId,
        isTimerRunning = isTimerRunning
    )
}

@OptIn(ExperimentalTime::class)
fun TaskWithIntervals.toProjectSession(): ProjectTask {
    return ProjectTask(
        projectTaskId = task.recordId,
        title = task.description,
        durationMillis = task.durationMillis,
        startDateTimeUtc = Instant.fromEpochMilliseconds(task.startDateTimeEpochMs),
        endDateTimeUtc = task.endDateTimeEpochMs?.let { Instant.fromEpochMilliseconds(it) },
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
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
        isFinished = isFinished,
        isTimerRunning = isTimerRunning
    )
}

fun TaskIntervalEntity.toSessionInterval(): TaskInterval {
    return TaskInterval(
        intervalId = intervalId,
        parentSessionId = parentTaskId,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        endDateTimeUtc = endDateTimeEpochMs?.let { Instant.fromEpochMilliseconds(it) },
        durationMillis = durationMillis
    )
}

fun TaskInterval.toSessionIntervalEntity(): TaskIntervalEntity {
    return TaskIntervalEntity(
        intervalId = intervalId ?: 0,
        parentTaskId = parentSessionId,
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
        durationMillis = durationMillis
    )
}