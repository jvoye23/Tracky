package com.jvcs.tracky.features.project.data.subtask

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.data.mappers.toSubTaskInterval
import com.jvcs.tracky.features.project.data.timer.closeSubTaskInterval
import com.jvcs.tracky.features.project.data.timer.closeTaskInterval
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
                projectDao.getOpenSubTaskIntervalForTask(taskId)?.let { projectDao.closeSubTaskInterval(it, now) }

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

    override suspend fun stopSubTask(subTaskId: String): Result<SubTaskInterval?, DataError.Local> {
        return try {
            val closed = withContext(dbWriteDispatcher) {
                val open = projectDao.getOpenSubTaskInterval(subTaskId) ?: return@withContext null
                val now = timeProvider.nowInstant
                val closed = projectDao.closeSubTaskInterval(open, now)

                // Only the subtask that opened the task's interval may close it again. A task the
                // user started stays running, and a sibling that merely nested inside it never
                // claimed it in the first place.
                if (open.startedParentTimer) {
                    projectDao.getIntervalById(open.parentTaskIntervalId)
                        ?.takeIf { it.endDateTimeEpochMs == null }
                        ?.let { projectDao.closeTaskInterval(it, now) }
                }
                closed
            }
            Result.Success(closed?.toSubTaskInterval())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }
}
