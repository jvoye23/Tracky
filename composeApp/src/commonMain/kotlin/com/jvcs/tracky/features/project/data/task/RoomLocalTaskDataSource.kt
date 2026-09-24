package com.jvcs.tracky.features.project.data.task

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.database.dao.SortOrderDao
import com.jvcs.tracky.core.database.dao.SubTaskDao
import com.jvcs.tracky.core.database.dao.SubTaskIntervalDao
import com.jvcs.tracky.core.database.dao.TaskDao
import com.jvcs.tracky.core.database.dao.TaskIntervalDao
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.device.DeviceIdProvider
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.ServerClock
import com.jvcs.tracky.core.domain.util.platformIoDispatcher
import com.jvcs.tracky.features.project.data.mappers.toProjectTask
import com.jvcs.tracky.features.project.data.mappers.toProjectTaskEntity
import com.jvcs.tracky.features.project.data.mappers.toTaskInterval
import com.jvcs.tracky.features.project.data.timer.closeSubTaskInterval
import com.jvcs.tracky.features.project.data.timer.closeTaskInterval
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.task.LocalTaskDataSource
import com.jvcs.tracky.features.project.domain.task.TaskTimerStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RoomLocalTaskDataSource(
    private val sortOrderDao: SortOrderDao,
    private val taskDao: TaskDao,
    private val subTaskDao: SubTaskDao,
    private val subTaskIntervalDao: SubTaskIntervalDao,
    private val taskIntervalDao: TaskIntervalDao,
    private val deviceIdProvider: DeviceIdProvider,
    /**
     * Timer boundaries are written on the corrected clock, never the raw device one. `TimeManager`
     * renders a running timer as `serverClock.now() - startedAt`; writing the start from
     * `timeProvider` put the two sides on different bases and injected the whole device-to-server
     * skew into the displayed duration — on every device, because the skew went into the row.
     */
    private val serverClock: ServerClock,
) : LocalTaskDataSource {

    // Same single-writer funnel as the other Room data sources — see RoomLocalProjectDataSource.
    private val dbWriteDispatcher = platformIoDispatcher.limitedParallelism(1)

    override fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> =
        taskDao
            .getTaskWithIntervalsById(taskId)
            .map { it?.toProjectTask() }

    override suspend fun getTaskById(taskId: String): Result<ProjectTask?, DataError.Local> =
        read {
            taskDao.getTaskWithIntervalsById(taskId).first()?.toProjectTask()
        }

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError.Local> =
        write {
            taskDao.upsertProjectTask(projectTask.toProjectTaskEntity())
        }

    override suspend fun deleteProjectTask(taskId: String): EmptyResult<DataError.Local> =
        write {
            taskDao.deleteProjectTask(taskId)
        }

    override suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long): EmptyResult<DataError.Local> =
        write {
            taskDao.updateTaskDuration(taskId, newDurationMillis)
        }

    override suspend fun getTaskSortIndices(projectId: String): Result<Map<String, Long?>, DataError.Local> =
        read {
            sortOrderDao.getTaskSortIndices(projectId).associate { it.projectTaskId to it.sortIndex }
        }

    override suspend fun updateTaskSortIndices(
        indices: Map<String, Long>,
        updatedAt: Instant,
    ): EmptyResult<DataError.Local> =
        write {
            sortOrderDao.updateTaskSortIndices(indices, updatedAt.toEpochMilliseconds())
        }

    override suspend fun updateTaskTitle(taskId: String, title: String): EmptyResult<DataError.Local> =
        write {
            taskDao.updateTaskTitle(taskId, title)
        }

    override suspend fun startTask(taskId: String): Result<TaskTimerStart, DataError.Local> {
        return try {
            // Read outside the write dispatcher: minting the id on first launch writes to
            // DataStore, and the single-writer funnel is for Room.
            val deviceId = deviceIdProvider.deviceId()
            // Read out here for the same reason as deviceId: the first call can touch DataStore,
            // and the write dispatcher is a single-writer funnel for Room.
            val startedAt = serverClock.now()
            val start =
                withContext(dbWriteDispatcher) {
                    // The owning project has to be read before the interval can be written: it is part
                    // of the row now, and the cascading foreign key would reject an interval whose task
                    // no longer exists anyway.
                    val task = taskDao.getTaskById(taskId) ?: return@withContext null

                    // Reuse whatever is already open rather than stacking a second row on top, the way
                    // startSubTask does. The timer lives only in memory, so a process death leaves the
                    // open interval behind with nothing tracking it; starting again would strand that
                    // row, and the next stop would close it with the whole wall-clock gap since.
                    taskIntervalDao.getOpenIntervalBySessionId(taskId)?.let { open ->
                        taskDao.updateSessionTimerStatus(taskId, true)
                        // openedInterval stays null: that row is already on the server, or queued for
                        // it, and pushing a CREATE for it a second time would be a duplicate.
                        return@withContext TaskTimerStart(open.toTaskInterval(), openedInterval = null)
                    }

                    val now = startedAt
                    val interval =
                        TaskIntervalEntity(
                            intervalId = Uuid.random().toString(),
                            parentTaskId = taskId,
                            parentProjectId = task.parentProjectId,
                            startDateTimeEpochMs = now.toEpochMilliseconds(),
                            endDateTimeEpochMs = null,
                            durationMillis = 0L,
                            startedByDeviceId = deviceId,
                        )

                    taskIntervalDao.upsertTaskInterval(interval)
                    taskDao.updateSessionTimerStatus(taskId, true)

                    val domain = interval.toTaskInterval()
                    TaskTimerStart(domain, openedInterval = domain)
                } ?: return Result.Error(DataError.Local.NOT_FOUND)
            Result.Success(start)
        } catch (exception: SQLiteException) {
            Logger.withTag("RoomLocalTaskDataSource").e(exception) { "startTask failed (SQLiteException)" }
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun stopTask(taskId: String): Result<TaskInterval?, DataError.Local> =
        try {
            val endedAt = serverClock.now()
            val closedInterval =
                withContext(dbWriteDispatcher) {
                    val openInterval = taskIntervalDao.getOpenIntervalBySessionId(taskId)
                    val updatedInterval =
                        if (openInterval != null) {
                            val now = endedAt
                            // A subtask cannot outlive the interval it sits in: leaving it open would strand
                            // a running subtask inside a closed task interval, which the foreign key permits
                            // but nothing could ever reconcile. It closes at the same instant the task does.
                            subTaskIntervalDao
                                .getOpenSubTaskIntervalForTask(taskId)
                                ?.let { subTaskIntervalDao.closeSubTaskInterval(it, now, subTaskDao) }

                            taskIntervalDao.closeTaskInterval(openInterval, now, taskDao)
                        } else {
                            null
                        }
                    taskDao.updateSessionTimerStatus(taskId, false)
                    updatedInterval
                }
            Result.Success(closedInterval?.toTaskInterval())
        } catch (exception: SQLiteException) {
            Logger.withTag("RoomLocalTaskDataSource").e(exception) { "stopTask failed (SQLiteException)" }
            Result.Error(DataError.Local.DISK_FULL)
        }

    private inline fun <T> read(block: () -> T): Result<T, DataError.Local> =
        try {
            Result.Success(block())
        } catch (exception: SQLiteException) {
            Logger.withTag("RoomLocalTaskDataSource").e(exception) { "read failed (SQLiteException)" }
            Result.Error(DataError.Local.UNKNOWN)
        }

    private suspend fun write(block: suspend () -> Unit): EmptyResult<DataError.Local> =
        try {
            withContext(dbWriteDispatcher) { block() }
            Result.Success(Unit)
        } catch (exception: SQLiteException) {
            Logger.withTag("RoomLocalTaskDataSource").e(exception) { "write failed (SQLiteException)" }
            Result.Error(DataError.Local.DISK_FULL)
        }
}
