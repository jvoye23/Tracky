package com.jvcs.tracky.features.project.data.task

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.data.mappers.toProjectSessionEntity
import com.jvcs.tracky.features.project.data.mappers.toProjectTask
import com.jvcs.tracky.features.project.data.mappers.toTaskInterval
import com.jvcs.tracky.features.project.data.timer.closeSubTaskInterval
import com.jvcs.tracky.features.project.data.timer.closeTaskInterval
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.task.LocalTaskDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RoomLocalTaskDataSource(
    private val projectDao: ProjectDao,
    private val timeProvider: TimeProvider
) : LocalTaskDataSource {

    // Same single-writer funnel as the other Room data sources — see RoomLocalProjectDataSource.
    private val dbWriteDispatcher = Dispatchers.IO.limitedParallelism(1)

    override fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> {
        return projectDao.getTaskWithIntervalsById(taskId)
            .map { it?.toProjectTask() }
    }

    override suspend fun getTaskById(taskId: String): Result<ProjectTask?, DataError.Local> = read {
        projectDao.getTaskWithIntervalsById(taskId).first()?.toProjectTask()
    }

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError.Local> = write {
        projectDao.upsertProjectRecord(projectTask.toProjectSessionEntity())
    }

    override suspend fun deleteProjectTask(taskId: String): EmptyResult<DataError.Local> = write {
        projectDao.deleteProjectRecord(taskId)
    }

    override suspend fun updateTaskDuration(
        taskId: String,
        newDurationMillis: Long
    ): EmptyResult<DataError.Local> = write {
        projectDao.updateTaskDuration(taskId, newDurationMillis)
    }

    override suspend fun updateTaskTitle(taskId: String, title: String): EmptyResult<DataError.Local> = write {
        projectDao.updateTaskTitle(taskId, title)
    }

    override suspend fun startTask(taskId: String): Result<TaskInterval, DataError.Local> {
        return try {
            val interval = withContext(dbWriteDispatcher) {
                // The owning project has to be read before the interval can be written: it is part
                // of the row now, and the cascading foreign key would reject an interval whose task
                // no longer exists anyway.
                val task = projectDao.getTaskById(taskId) ?: return@withContext null
                val now = timeProvider.nowInstant
                val interval = TaskIntervalEntity(
                    intervalId = Uuid.random().toString(),
                    parentTaskId = taskId,
                    parentProjectId = task.parentProjectId,
                    startDateTimeEpochMs = now.toEpochMilliseconds(),
                    endDateTimeEpochMs = null,
                    durationMillis = 0L
                )
                projectDao.upsertTaskInterval(interval)
                projectDao.updateSessionTimerStatus(taskId, true)
                interval
            } ?: return Result.Error(DataError.Local.NOT_FOUND)
            Result.Success(interval.toTaskInterval())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun stopTask(taskId: String): Result<TaskInterval?, DataError.Local> {
        return try {
            val closedInterval = withContext(dbWriteDispatcher) {
                val openInterval = projectDao.getOpenIntervalBySessionId(taskId)
                val updatedInterval = if (openInterval != null) {
                    val now = timeProvider.nowInstant
                    // A subtask cannot outlive the interval it sits in: leaving it open would strand
                    // a running subtask inside a closed task interval, which the foreign key permits
                    // but nothing could ever reconcile. It closes at the same instant the task does.
                    projectDao.getOpenSubTaskIntervalForTask(taskId)
                        ?.let { projectDao.closeSubTaskInterval(it, now) }

                    projectDao.closeTaskInterval(openInterval, now)
                } else null
                projectDao.updateSessionTimerStatus(taskId, false)
                updatedInterval
            }
            Result.Success(closedInterval?.toTaskInterval())
        } catch (e: Exception) {
            if(e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    private inline fun <T> read(block: () -> T): Result<T, DataError.Local> {
        return try {
            Result.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    private suspend fun write(block: suspend () -> Unit): EmptyResult<DataError.Local> {
        return try {
            withContext(dbWriteDispatcher) { block() }
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(DataError.Local.DISK_FULL)
        }
    }
}
