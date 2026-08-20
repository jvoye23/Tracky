package com.jvcs.tracky.features.project.data.interval

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.data.mappers.toTaskInterval
import com.jvcs.tracky.features.project.data.mappers.toTaskIntervalEntity
import com.jvcs.tracky.features.project.domain.interval.LocalIntervalDataSource
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class RoomLocalIntervalDataSource(
    private val projectDao: ProjectDao
) : LocalIntervalDataSource {

    // Same single-writer funnel as the other Room data sources — see RoomLocalProjectDataSource.
    private val dbWriteDispatcher = Dispatchers.IO.limitedParallelism(1)

    override suspend fun upsertTaskInterval(interval: TaskInterval): EmptyResult<DataError.Local> = write {
        projectDao.upsertTaskInterval(interval.toTaskIntervalEntity())
    }

    override suspend fun getIntervalById(intervalId: String): Result<TaskInterval?, DataError.Local> = read {
        projectDao.getIntervalById(intervalId)?.toTaskInterval()
    }

    override suspend fun getOpenIntervalByTaskId(taskId: String): Result<TaskInterval?, DataError.Local> = read {
        projectDao.getOpenIntervalBySessionId(taskId)?.toTaskInterval()
    }

    override suspend fun deleteTaskInterval(intervalId: String): EmptyResult<DataError.Local> = write {
        projectDao.deleteTaskInterval(intervalId)
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
