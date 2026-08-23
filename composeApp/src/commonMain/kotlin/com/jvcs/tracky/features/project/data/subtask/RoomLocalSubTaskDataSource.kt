package com.jvcs.tracky.features.project.data.subtask

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.data.mappers.toSubTaskInterval
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.subtask.LocalSubTaskDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RoomLocalSubTaskDataSource(
    private val projectDao: ProjectDao,
    private val timeProvider: TimeProvider
) : LocalSubTaskDataSource {

    // Same single-writer funnel as the other Room data sources — see RoomLocalProjectDataSource.
    private val dbWriteDispatcher = Dispatchers.IO.limitedParallelism(1)

    override suspend fun startSubTask(subTaskId: String): Result<SubTaskInterval, DataError.Local> {
        return try {
            val interval = withContext(dbWriteDispatcher) {
                val subTask = projectDao.getSubTaskById(subTaskId) ?: return@withContext null
                val taskId = subTask.parentProjectTaskId
                val now = timeProvider.nowInstant

                // One subtask at a time: whichever sibling is still running gets closed at the same
                // instant this one starts, so their durations never overlap.
                projectDao.getOpenSubTaskIntervalForTask(taskId)?.let { closeSubTaskInterval(it, now) }

                // The enclosing task interval. Reusing the open one keeps a manually started task
                // timer intact; opening one makes this subtask the reason the task is running, which
                // startedParentTimer records so stopping it can undo exactly that.
                val openTaskInterval = projectDao.getOpenIntervalBySessionId(taskId)
                val startedParentTimer = openTaskInterval == null
                val taskInterval = openTaskInterval ?: TaskIntervalEntity(
                    intervalId = Uuid.random().toString(),
                    parentTaskId = taskId,
                    parentProjectId = subTask.parentProjectId,
                    startDateTimeEpochMs = now.toEpochMilliseconds(),
                    endDateTimeEpochMs = null,
                    durationMillis = 0L
                ).also {
                    projectDao.upsertTaskInterval(it)
                    projectDao.updateSessionTimerStatus(taskId, true)
                }

                val interval = SubTaskIntervalEntity(
                    subTaskIntervalId = Uuid.random().toString(),
                    parentSubTaskId = subTaskId,
                    parentTaskIntervalId = taskInterval.intervalId,
                    parentProjectId = subTask.parentProjectId,
                    startDateTimeEpochMs = now.toEpochMilliseconds(),
                    endDateTimeEpochMs = null,
                    durationMillis = 0L,
                    startedParentTimer = startedParentTimer
                )
                projectDao.upsertSubTaskInterval(interval)
                projectDao.updateSubTaskTimerStatus(subTaskId, true)
                interval
            } ?: return Result.Error(DataError.Local.NOT_FOUND)
            Result.Success(interval.toSubTaskInterval())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    /**
     * Closes [interval] at [now], banks its duration on the subtask and clears the subtask's flag.
     *
     * Deliberately says nothing about the parent task: the caller decides that. Starting a sibling
     * leaves the task running, which is the only case this slice has.
     */
    private suspend fun closeSubTaskInterval(interval: SubTaskIntervalEntity, now: Instant) {
        val start = Instant.fromEpochMilliseconds(interval.startDateTimeEpochMs)
        val duration = (now - start).inWholeMilliseconds
        projectDao.upsertSubTaskInterval(
            interval.copy(endDateTimeEpochMs = now.toEpochMilliseconds(), durationMillis = duration)
        )
        projectDao.addSubTaskDuration(interval.parentSubTaskId, duration)
        projectDao.updateSubTaskTimerStatus(interval.parentSubTaskId, false)
    }
}
