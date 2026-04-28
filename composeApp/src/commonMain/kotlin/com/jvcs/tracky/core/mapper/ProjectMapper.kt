package com.jvcs.tracky.core.mapper

import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSessionEntity
import com.jvcs.tracky.core.database.relation.ProjectWithSessions
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectSession
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
        endDateTimeUtc = if (endDateTimeEpochMs == null) null else
            Instant.fromEpochMilliseconds(endDateTimeEpochMs),
        //projectRecords = projectRecords
    )
}

@OptIn(ExperimentalTime::class)
fun ProjectWithSessions.toProject(): Project {
    return Project(
        projectId = project.projectId,
        title = project.title,
        description = project.description,
        colorArgb = project.color,
        totalDurationMillis = project.totalDuration,
        startDateTimeUtc = Instant.fromEpochMilliseconds(project.startDateTimeEpochMs),
        isFinished = project.isFinished,
        endDateTimeUtc = if (project.endDateTimeEpochMs == null) null else
            Instant.fromEpochMilliseconds(project.endDateTimeEpochMs),
        projectSessions = projectSessions.map { it.toProjectSession() }
    )
}

@OptIn(ExperimentalTime::class)
fun ProjectSessionEntity.toProjectSession(): ProjectSession {
    return ProjectSession(
        projectSessionId = recordId,
        title = description,
        durationMillis = durationMillis,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        endDateTimeUtc = if (endDateTimeEpochMs == null) null else
            Instant.fromEpochMilliseconds(endDateTimeEpochMs),
        isFinished = isFinished,
        parentProjectId = parentProjectId,
        isTimerRunning = isTimerRunning
    )
}

fun ProjectSession.toProjectSessionEntity(): ProjectSessionEntity {
    return ProjectSessionEntity(
        recordId = projectSessionId,
        parentProjectId = parentProjectId,
        description = title,
        durationMillis = durationMillis ?: 0L,
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
        isFinished = isFinished,
        isTimerRunning = isTimerRunning
    )
}