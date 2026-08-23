package com.jvcs.tracky.features.project.data.subtaskinterval

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.data.mappers.toSubTaskInterval
import com.jvcs.tracky.features.project.data.mappers.toSubTaskIntervalEntity
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.subtaskinterval.LocalSubTaskIntervalDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class RoomLocalSubTaskIntervalDataSource(
    private val projectDao: ProjectDao
) : LocalSubTaskIntervalDataSource {

    // Same single-writer funnel as the other Room data sources — see RoomLocalProjectDataSource.
    private val dbWriteDispatcher = Dispatchers.IO.limitedParallelism(1)

    override suspend fun upsertSubTaskInterval(interval: SubTaskInterval): EmptyResult<DataError.Local> = write {
        projectDao.upsertSubTaskInterval(interval.toSubTaskIntervalEntity())
    }

    override suspend fun getSubTaskIntervalById(
        intervalId: String
    ): Result<SubTaskInterval?, DataError.Local> = read {
        projectDao.getSubTaskIntervalById(intervalId)?.toSubTaskInterval()
    }

    override suspend fun getOpenIntervalBySubTaskId(
        subTaskId: String
    ): Result<SubTaskInterval?, DataError.Local> = read {
        projectDao.getOpenSubTaskInterval(subTaskId)?.toSubTaskInterval()
    }

    override suspend fun deleteSubTaskInterval(intervalId: String): EmptyResult<DataError.Local> = write {
        projectDao.deleteSubTaskInterval(intervalId)
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
