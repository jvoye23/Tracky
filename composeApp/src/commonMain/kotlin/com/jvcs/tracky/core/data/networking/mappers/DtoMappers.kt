@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.core.data.networking.mappers

import com.jvcs.tracky.core.data.networking.dto.ProjectDto
import com.jvcs.tracky.core.data.networking.dto.ProjectSubTaskDto
import com.jvcs.tracky.core.data.networking.dto.ProjectTaskDto
import com.jvcs.tracky.core.data.networking.dto.SubTaskIntervalDto
import com.jvcs.tracky.core.data.networking.dto.TaskIntervalDto
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun ProjectDto.toProject(): Project =
    Project(
        projectId = id,
        title = title,
        description = description,
        colorArgb = color,
        totalDurationMillis = totalDuration,
        startDateTimeUtc = Instant.parse(startDateTimeUtc),
        isFinished = finished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeUtc?.let(Instant::parse),
        projectTasks = tasks?.map { it.toProjectTask(id) }.orEmpty(),
        isArchived = isArchived,
        trashedAt = trashedAt?.let(Instant::parse),
        isPinned = isPinned,
        ownUpdatedAt = updatedAt?.let(Instant::parse),
        sortIndex = sortIndex,
    )

fun ProjectTaskDto.toProjectTask(parentProjectId: String): ProjectTask =
    ProjectTask(
        projectTaskId = id,
        // Before API 1.6.0 the wire had no title field and the client put the task's title in
        // description. The fallback keeps a device talking to an older deployment readable; against
        // 1.6.0 and later, title is always populated and description is a separate (unused) field.
        title = title.ifBlank { description.orEmpty() },
        // When that fallback fires, description *is* the title, so carrying it across as well
        // would write the title into the task's own description column - exactly the conflation
        // this field was split out to end.
        description = description.takeIf { title.isNotBlank() },
        durationMillis = durationMillis,
        startDateTimeUtc = Instant.parse(startDateTimeUtc),
        endDateTimeUtc = endDateTimeUtc?.let(Instant::parse),
        isFinished = isFinished,
        parentProjectId = parentProjectId,
        isTimerRunning = isTimerRunning,
        intervals = intervals.map { it.toTaskInterval(parentProjectId, it.startedByDeviceId) },
        ownUpdatedAt = updatedAt?.let(Instant::parse),
        subTasks = subTasks.map { it.toProjectSubTask(parentProjectId) },
        sortIndex = sortIndex,
    )

// Like intervals, subtasks are nested inside their task on the wire and the project id is never
// repeated, so it is handed down from the enclosing ProjectTaskDto.
fun ProjectSubTaskDto.toProjectSubTask(parentProjectId: String): ProjectSubTask =
    ProjectSubTask(
        projectSubTaskId = subTaskId,
        parentProjectTaskId = parentProjectTaskId,
        parentProjectId = parentProjectId,
        title = title,
        description = description,
        durationMillis = durationMillis,
        isTimerRunning = isTimerRunning,
        startDateTimeUtc = Instant.parse(startDateTimeUtc),
        endDateTimeUtc = endDateTimeUtc?.let(Instant::parse),
        isFinished = isFinished,
        // startedParentTimer is unknowable from the wire; upsertServerTree keeps whatever the
        // local row already had, and false is safe for a row this device has never seen.
        // startedByDeviceId is on the wire, so it is carried across rather than invented.
        subTaskIntervals =
            intervals.map {
                it.toSubTaskInterval(parentProjectId, startedParentTimer = false, it.startedByDeviceId)
            },
        ownUpdatedAt = updatedAt?.let(Instant::parse),
        sortIndex = sortIndex,
    )

/**
 * Rebuilds a subtask interval from a server payload missing one of its fields.
 *
 * [startedParentTimer] is a parameter rather than a default precisely so a caller cannot forget it:
 * the happy path of a push writes the server's echo straight back to Room, and a defaulted `false`
 * would quietly break "stopping this subtask also stops its parent task". A push passes the value
 * off the row it sent; a pull has no way to know it and passes `false`, which the merge in
 * ServerTreeWriter.upsertServerTree then overrides with whatever the local row already held.
 *
 * [startedByDeviceId] is a parameter for the same reason and behaves the same way: which device
 * opened an interval is not on the wire, so a push passes the value off the row it sent and a pull
 * passes null for the merge to preserve.
 */
fun SubTaskIntervalDto.toSubTaskInterval(
    parentProjectId: String,
    startedParentTimer: Boolean,
    startedByDeviceId: String?,
): SubTaskInterval =
    SubTaskInterval(
        subTaskIntervalId = subTaskIntervalId,
        parentTaskIntervalId = parentTaskIntervalId,
        parentSubTaskId = parentSubTaskId,
        parentProjectId = parentProjectId,
        startDateTimeUtc = Instant.parse(startDateTimeUtc),
        endDateTimeUtc = endDateTimeUtc?.let(Instant::parse),
        durationMillis = durationMillis,
        startedParentTimer = startedParentTimer,
        startedByDeviceId = startedByDeviceId,
    )

// The wire payload nests intervals inside their task and never repeats the project id, so it is
// handed down from the enclosing ProjectTaskDto rather than read off the interval itself.
fun TaskIntervalDto.toTaskInterval(parentProjectId: String, startedByDeviceId: String?): TaskInterval =
    TaskInterval(
        intervalId = intervalId,
        parentTaskId = parentSessionId,
        parentProjectId = parentProjectId,
        startDateTimeUtc = Instant.parse(startDateTimeUtc),
        endDateTimeUtc = endDateTimeUtc?.let(Instant::parse),
        durationMillis = durationMillis,
        startedByDeviceId = startedByDeviceId,
    )

fun Project.toProjectDto(): ProjectDto =
    ProjectDto(
        id = projectId,
        title = title,
        description = description,
        color = colorArgb,
        totalDuration = totalDurationMillis,
        startDateTimeUtc = startDateTimeUtc.toString(),
        finished = isFinished,
        useLightTextColor = useLightTextColor,
        endDateTimeUtc = endDateTimeUtc?.toString(),
        tasks = projectTasks?.map { it.toProjectTaskDto() },
        isArchived = isArchived,
        trashedAt = trashedAt?.toString(),
        isPinned = isPinned,
        updatedAt = ownUpdatedAt?.toString(),
        sortIndex = sortIndex,
    )

fun ProjectTask.toProjectTaskDto(): ProjectTaskDto =
    ProjectTaskDto(
        id = projectTaskId,
        title = title,
        description = description,
        durationMillis = durationMillis,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        isFinished = isFinished,
        isTimerRunning = isTimerRunning,
        intervals = intervals.map { it.toTaskIntervalDto() },
        subTasks = subTasks.orEmpty().map { it.toProjectSubTaskDto() },
        updatedAt = ownUpdatedAt?.toString(),
    )

fun ProjectSubTask.toProjectSubTaskDto(): ProjectSubTaskDto =
    ProjectSubTaskDto(
        subTaskId = projectSubTaskId,
        parentProjectTaskId = parentProjectTaskId,
        title = title,
        description = description,
        durationMillis = durationMillis,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        isFinished = isFinished,
        isTimerRunning = isTimerRunning,
        intervals = subTaskIntervals.map { it.toSubTaskIntervalDto() },
        updatedAt = ownUpdatedAt?.toString(),
    )

fun SubTaskInterval.toSubTaskIntervalDto(): SubTaskIntervalDto =
    SubTaskIntervalDto(
        subTaskIntervalId = subTaskIntervalId,
        parentSubTaskId = parentSubTaskId,
        parentTaskIntervalId = parentTaskIntervalId,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        durationMillis = durationMillis,
    )

fun TaskInterval.toTaskIntervalDto(): TaskIntervalDto =
    TaskIntervalDto(
        intervalId = intervalId,
        parentSessionId = parentTaskId,
        startDateTimeUtc = startDateTimeUtc.toString(),
        endDateTimeUtc = endDateTimeUtc?.toString(),
        durationMillis = durationMillis,
    )
