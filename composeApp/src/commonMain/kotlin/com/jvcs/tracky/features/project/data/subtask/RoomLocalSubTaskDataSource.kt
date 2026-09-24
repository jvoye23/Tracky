package com.jvcs.tracky.features.project.data.subtask

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.database.dao.SortOrderDao
import com.jvcs.tracky.core.database.dao.StrandedIntervalDao
import com.jvcs.tracky.core.database.dao.SubTaskDao
import com.jvcs.tracky.core.database.dao.SubTaskIntervalDao
import com.jvcs.tracky.core.database.dao.TaskDao
import com.jvcs.tracky.core.database.dao.TaskIntervalDao
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.device.DeviceIdProvider
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.ServerClock
import com.jvcs.tracky.core.domain.util.platformIoDispatcher
import com.jvcs.tracky.features.project.data.mappers.toProjectSubTask
import com.jvcs.tracky.features.project.data.mappers.toProjectSubTaskEntity
import com.jvcs.tracky.features.project.data.mappers.toSubTaskInterval
import com.jvcs.tracky.features.project.data.mappers.toTaskInterval
import com.jvcs.tracky.features.project.data.timer.closeSubTaskInterval
import com.jvcs.tracky.features.project.data.timer.closeTaskInterval
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.subtask.LocalSubTaskDataSource
import com.jvcs.tracky.features.project.domain.subtask.SubTaskTimerChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RoomLocalSubTaskDataSource(
    private val sortOrderDao: SortOrderDao,
    private val subTaskDao: SubTaskDao,
    private val taskDao: TaskDao,
    private val subTaskIntervalDao: SubTaskIntervalDao,
    private val taskIntervalDao: TaskIntervalDao,
    private val strandedIntervalDao: StrandedIntervalDao,
    private val deviceIdProvider: DeviceIdProvider,
    /** See [com.jvcs.tracky.features.project.data.task.RoomLocalTaskDataSource]'s serverClock. */
    private val serverClock: ServerClock,
) : LocalSubTaskDataSource {

    // Same single-writer funnel as the other Room data sources — see projectWriteDispatcher in RoomCalls.kt.
    private val dbWriteDispatcher = platformIoDispatcher.limitedParallelism(1)

    override fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>> =
        subTaskDao.getSubTasksWithIntervals(taskId).map { rows -> rows.map { it.toProjectSubTask() } }

    override suspend fun getSubTaskById(subTaskId: String): Result<ProjectSubTask?, DataError.Local> =
        read {
            subTaskDao.getSubTaskById(subTaskId)?.toProjectSubTask()
        }

    override suspend fun lastStartedSubTaskId(taskId: String): Result<String?, DataError.Local> =
        read {
            subTaskDao.getLastStartedSubTaskId(taskId)
        }

    override suspend fun getSubTaskSortIndices(taskId: String): Result<Map<String, Long?>, DataError.Local> =
        read {
            sortOrderDao.getSubTaskSortIndices(taskId).associate { it.projectSubTaskId to it.sortIndex }
        }

    override suspend fun updateSubTaskSortIndices(
        indices: Map<String, Long>,
        updatedAt: Instant,
    ): EmptyResult<DataError.Local> =
        write {
            sortOrderDao.updateSubTaskSortIndices(indices, updatedAt.toEpochMilliseconds())
        }

    override suspend fun upsertSubTask(subTask: ProjectSubTask): EmptyResult<DataError.Local> =
        write { subTaskDao.upsertProjectSubTask(subTask.toProjectSubTaskEntity()) }

    override suspend fun deleteSubTask(subTaskId: String): EmptyResult<DataError.Local> =
        write { subTaskDao.deleteProjectSubTask(subTaskId) }

    override suspend fun startSubTask(subTaskId: String): Result<SubTaskTimerChange, DataError.Local> {
        return try {
            // Outside the write dispatcher for the same reason as RoomLocalTaskDataSource.startTask.
            val deviceId = deviceIdProvider.deviceId()
            val startedAt = serverClock.now()
            val change =
                withContext(dbWriteDispatcher) {
                    val subTask = subTaskDao.getSubTaskById(subTaskId) ?: return@withContext null
                    val taskId = subTask.parentProjectTaskId
                    val now = startedAt

                    // One subtask at a time: whichever sibling is still running gets closed at the same
                    // instant this one starts, so their durations never overlap.
                    subTaskIntervalDao
                        .getOpenSubTaskIntervalForTask(taskId)
                        ?.let { subTaskIntervalDao.closeSubTaskInterval(it, now, subTaskDao) }

                    // The enclosing task interval. Reusing the open one keeps a manually started task
                    // timer intact; opening one makes this subtask the reason the task is running, which
                    // startedParentTimer records so stopping it can undo exactly that.
                    val openTaskInterval = taskIntervalDao.getOpenIntervalBySessionId(taskId)
                    val parentTaskInterval =
                        openTaskInterval
                            ?: TaskIntervalEntity(
                                intervalId = Uuid.random().toString(),
                                parentTaskId = taskId,
                                parentProjectId = subTask.parentProjectId,
                                startDateTimeEpochMs = now.toEpochMilliseconds(),
                                endDateTimeEpochMs = null,
                                durationMillis = 0L,
                                startedByDeviceId = deviceId,
                            ).also {
                                taskIntervalDao.upsertTaskInterval(it)
                                taskDao.updateSessionTimerStatus(taskId, true)
                            }
                    // Non-null only when this subtask opened the task's interval.
                    val opened = parentTaskInterval.takeIf { openTaskInterval == null }

                    val interval =
                        SubTaskIntervalEntity(
                            subTaskIntervalId = Uuid.random().toString(),
                            parentSubTaskId = subTaskId,
                            parentTaskIntervalId = parentTaskInterval.intervalId,
                            parentProjectId = subTask.parentProjectId,
                            startDateTimeEpochMs = now.toEpochMilliseconds(),
                            endDateTimeEpochMs = null,
                            durationMillis = 0L,
                            startedParentTimer = opened != null,
                            startedByDeviceId = deviceId,
                        )
                    subTaskIntervalDao.upsertSubTaskInterval(interval)
                    subTaskDao.updateSubTaskTimerStatus(subTaskId, true)

                    SubTaskTimerChange(
                        subTaskInterval = interval.toSubTaskInterval(),
                        taskInterval = opened?.toTaskInterval(),
                    )
                } ?: return Result.Error(DataError.Local.NOT_FOUND)
            Result.Success(change)
        } catch (exception: SQLiteException) {
            Logger.withTag("RoomLocalSubTaskDataSource").e(exception) { "startSubTask failed (SQLiteException)" }
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun stopSubTask(subTaskId: String): Result<SubTaskTimerChange?, DataError.Local> {
        return try {
            val endedAt = serverClock.now()
            val change =
                withContext(dbWriteDispatcher) {
                    val open = subTaskIntervalDao.getOpenSubTaskInterval(subTaskId) ?: return@withContext null
                    val now = endedAt
                    val closed = subTaskIntervalDao.closeSubTaskInterval(open, now, subTaskDao)

                    // Only the subtask that opened the task's interval may close it again. A task the
                    // user started stays running, and a sibling that merely nested inside it never
                    // claimed it in the first place.
                    //
                    // The "still open" check reads the row directly rather than through
                    // getOpenIntervalBySessionId, so it needs its own stranded guard: a parked interval
                    // is open but untimed, and closing it here would bank every hour since it opened.
                    val closedTaskInterval =
                        if (open.startedParentTimer) {
                            taskIntervalDao
                                .getIntervalById(open.parentTaskIntervalId)
                                ?.takeIf { it.endDateTimeEpochMs == null }
                                ?.takeIf { strandedIntervalDao.getStrandedInterval(it.intervalId) == null }
                                ?.let { taskIntervalDao.closeTaskInterval(it, now, taskDao) }
                        } else {
                            null
                        }

                    SubTaskTimerChange(
                        subTaskInterval = closed.toSubTaskInterval(),
                        taskInterval = closedTaskInterval?.toTaskInterval(),
                    )
                }
            Result.Success(change)
        } catch (exception: SQLiteException) {
            Logger.withTag("RoomLocalSubTaskDataSource").e(exception) { "stopSubTask failed (SQLiteException)" }
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    private inline fun <T> read(block: () -> T): Result<T, DataError.Local> =
        try {
            Result.Success(block())
        } catch (exception: SQLiteException) {
            Logger.withTag("RoomLocalSubTaskDataSource").e(exception) { "read failed (SQLiteException)" }
            Result.Error(DataError.Local.UNKNOWN)
        }

    private suspend fun write(block: suspend () -> Unit): EmptyResult<DataError.Local> =
        try {
            withContext(dbWriteDispatcher) { block() }
            Result.Success(Unit)
        } catch (exception: SQLiteException) {
            Logger.withTag("RoomLocalSubTaskDataSource").e(exception) { "write failed (SQLiteException)" }
            Result.Error(DataError.Local.DISK_FULL)
        }
}
