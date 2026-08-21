package com.jvcs.tracky.features.project.data.timer

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import kotlin.time.Instant

/**
 * Closing an interval, shared by the two data sources that have to do it.
 *
 * Stopping a task and stopping a subtask each have to close rows at the *other* level — a task
 * cannot stop while a subtask under it is still running, and a subtask that started its parent's
 * timer has to stop it again. Both directions bank the duration the same way, so the arithmetic
 * lives here once rather than being written twice with two chances to drift.
 */

/**
 * Closes [interval] at [now], banks its duration on the task and clears the task's timer flag.
 *
 * Returns the closed row so callers can hand it on without recomputing the duration.
 */
internal suspend fun ProjectDao.closeTaskInterval(
    interval: TaskIntervalEntity,
    now: Instant
): TaskIntervalEntity {
    val duration = interval.elapsedAt(now)
    val closed =
        interval.copy(endDateTimeEpochMs = now.toEpochMilliseconds(), durationMillis = duration)
    upsertTaskInterval(closed)
    addTaskDuration(interval.parentTaskId, duration)
    updateSessionTimerStatus(interval.parentTaskId, false)
    return closed
}

/** The same for a subtask interval, likewise returning the row it closed. */
internal suspend fun ProjectDao.closeSubTaskInterval(
    interval: SubTaskIntervalEntity,
    now: Instant
): SubTaskIntervalEntity {
    val duration = interval.elapsedAt(now)
    val closed =
        interval.copy(endDateTimeEpochMs = now.toEpochMilliseconds(), durationMillis = duration)
    upsertSubTaskInterval(closed)
    addSubTaskDuration(interval.parentSubTaskId, duration)
    updateSubTaskTimerStatus(interval.parentSubTaskId, false)
    return closed
}

private fun TaskIntervalEntity.elapsedAt(now: Instant): Long =
    (now - Instant.fromEpochMilliseconds(startDateTimeEpochMs)).inWholeMilliseconds

private fun SubTaskIntervalEntity.elapsedAt(now: Instant): Long =
    (now - Instant.fromEpochMilliseconds(startDateTimeEpochMs)).inWholeMilliseconds
