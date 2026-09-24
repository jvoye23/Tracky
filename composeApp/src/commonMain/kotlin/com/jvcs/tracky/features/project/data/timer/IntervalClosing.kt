package com.jvcs.tracky.features.project.data.timer

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.dao.SubTaskIntervalDao
import com.jvcs.tracky.core.database.dao.TaskDao
import com.jvcs.tracky.core.database.dao.TaskIntervalDao
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
internal suspend fun TaskIntervalDao.closeTaskInterval(
    interval: TaskIntervalEntity,
    now: Instant,
    taskDao: TaskDao,
): TaskIntervalEntity {
    val duration = interval.elapsedAt(now)
    val closed =
        interval.copy(endDateTimeEpochMs = now.toEpochMilliseconds(), durationMillis = duration)

    upsertTaskInterval(closed)
    taskDao.addTaskDuration(interval.parentTaskId, duration)
    taskDao.updateSessionTimerStatus(interval.parentTaskId, false)
    return closed
}

/** The same for a subtask interval, likewise returning the row it closed. */
internal suspend fun SubTaskIntervalDao.closeSubTaskInterval(
    interval: SubTaskIntervalEntity,
    now: Instant,
    subTaskDao: ProjectDao,
): SubTaskIntervalEntity {
    val duration = interval.elapsedAt(now)
    val closed =
        interval.copy(endDateTimeEpochMs = now.toEpochMilliseconds(), durationMillis = duration)

    upsertSubTaskInterval(closed)
    subTaskDao.addSubTaskDuration(interval.parentSubTaskId, duration)
    subTaskDao.updateSubTaskTimerStatus(interval.parentSubTaskId, false)
    return closed
}

/**
 * Floored at zero, mirroring [com.jvcs.tracky.features.project.domain.timer.RunningTimer.elapsedAt].
 *
 * The closed duration is added straight to the parent's running total, so a negative one silently
 * subtracts time the user did track. It should now be unreachable — both ends are written on the
 * corrected clock — but the cost of being wrong here is corrupted totals, and the cost of the guard
 * is a comparison.
 */
private fun TaskIntervalEntity.elapsedAt(now: Instant): Long =
    (now - Instant.fromEpochMilliseconds(startDateTimeEpochMs)).inWholeMilliseconds.coerceAtLeast(0)

private fun SubTaskIntervalEntity.elapsedAt(now: Instant): Long =
    (now - Instant.fromEpochMilliseconds(startDateTimeEpochMs)).inWholeMilliseconds.coerceAtLeast(0)
