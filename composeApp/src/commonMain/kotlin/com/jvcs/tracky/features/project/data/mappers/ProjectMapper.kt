@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project.data.mappers

import com.jvcs.tracky.core.data.networking.CreateProjectRequest
import com.jvcs.tracky.core.data.networking.UpdateProjectRequest
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.relation.ProjectLastTrackedEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTaskTreeEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTasksEntity
import com.jvcs.tracky.core.database.relation.TaskWithSubTasks
import com.jvcs.tracky.features.project.domain.interval.lastTrackedEnd
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.task.sortedByTaskOrder
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun Project.toProjectEntity(): ProjectEntity =
    ProjectEntity(
        projectId = projectId,
        title = title,
        description = description,
        color = colorArgb,
        totalDuration = totalDurationMillis,
        startDateTimeEpochMs = startDateTimeUtc.toEpochMilliseconds(),
        isFinished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeEpochMs = endDateTimeUtc?.toEpochMilliseconds(),
        isArchived = isArchived,
        trashedAtEpochMs = trashedAt?.toEpochMilliseconds(),
        isPinned = isPinned,
        updatedAtEpochMs = ownUpdatedAt?.toEpochMilliseconds(),
        sortIndex = sortIndex,
    )

fun ProjectEntity.toProject(): Project =
    Project(
        projectId = projectId,
        title = title,
        description = description,
        colorArgb = color,
        totalDurationMillis = totalDuration,
        startDateTimeUtc = Instant.fromEpochMilliseconds(startDateTimeEpochMs),
        isFinished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        isArchived = isArchived,
        trashedAt = trashedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        isPinned = isPinned,
        ownUpdatedAt = updatedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        sortIndex = sortIndex,
    )

fun ProjectWithTasksEntity.toProject(): Project =
    Project(
        projectId = project.projectId,
        title = project.title,
        description = project.description,
        colorArgb = project.color,
        totalDurationMillis = project.totalDuration,
        startDateTimeUtc = Instant.fromEpochMilliseconds(project.startDateTimeEpochMs),
        isFinished = project.isFinished,
        useLightTextColor = project.useLightTextColor,
        endDateTimeUtc = project.endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        projectTasks = projectTasks.map { it.toProjectTask() }.sortedByTaskOrder(),
        isArchived = project.isArchived,
        trashedAt = project.trashedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        isPinned = project.isPinned,
        ownUpdatedAt = project.updatedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        sortIndex = project.sortIndex,
    )

/**
 * A list read joined with [lastTracked] from ProjectTreeDao.observeLastTrackedEnds. Its relation
 * graph carries no intervals, so this is where those projects get [Project.lastTrackedEndUtc].
 */
fun List<ProjectWithTasksEntity>.toProjects(lastTracked: List<ProjectLastTrackedEntity>): List<Project> {
    val endByProjectId = lastTracked.associate { it.projectId to it.lastTrackedEndEpochMs }
    return map { entity ->
        entity.toProject().copy(
            lastTrackedEndUtc = endByProjectId[entity.project.projectId]?.let(Instant::fromEpochMilliseconds),
        )
    }
}

/**
 * Same as [ProjectWithTasksEntity.toProject], except the tasks keep their intervals and subtasks
 * because they map through the [TaskWithSubTasks] overload of `toProjectTask`.
 */
fun ProjectWithTaskTreeEntity.toProject(): Project {
    val tasks = projectTasks.map { it.toProjectTask() }.sortedByTaskOrder()
    return Project(
        projectId = project.projectId,
        title = project.title,
        description = project.description,
        colorArgb = project.color,
        totalDurationMillis = project.totalDuration,
        startDateTimeUtc = Instant.fromEpochMilliseconds(project.startDateTimeEpochMs),
        isFinished = project.isFinished,
        useLightTextColor = project.useLightTextColor,
        endDateTimeUtc = project.endDateTimeEpochMs?.let(Instant::fromEpochMilliseconds),
        projectTasks = tasks,
        isArchived = project.isArchived,
        trashedAt = project.trashedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        isPinned = project.isPinned,
        ownUpdatedAt = project.updatedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        sortIndex = project.sortIndex,
        lastTrackedEndUtc = tasks.flatMap { it.intervals }.lastTrackedEnd(),
    )
}

fun Project.toCreateProjectRequest(): CreateProjectRequest =
    CreateProjectRequest(
        id = projectId,
        title = title,
        description = description.orEmpty(),
        color = colorArgb ?: 0,
        startDateTimeUtc = startDateTimeUtc.toString(),
        useLightTextColor = useLightTextColor,
        updatedAtUtc = ownUpdatedAt?.toString(),
        sortIndex = sortIndex,
    )

fun Project.toUpdateProjectRequest(): UpdateProjectRequest =
    UpdateProjectRequest(
        title = title,
        description = description,
        color = colorArgb,
        totalDuration = totalDurationMillis,
        startDateTimeUtc = startDateTimeUtc.toString(),
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeUtc?.toString(),
        trashedAtUtc = trashedAt?.toString(),
        isPinned = isPinned,
        isFinished = isFinished,
        isArchived = isArchived,
        updatedAtUtc = ownUpdatedAt?.toString(),
        sortIndex = sortIndex,
    )
