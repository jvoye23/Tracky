package com.jvcs.tracky.features.project.data.interval

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.database.dao.TaskIntervalDao
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.platformIoDispatcher
import com.jvcs.tracky.features.project.data.mappers.toTaskInterval
import com.jvcs.tracky.features.project.data.mappers.toTaskIntervalEntity
import com.jvcs.tracky.features.project.domain.interval.LocalIntervalDataSource
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class RoomLocalIntervalDataSource(private val taskIntervalDao: TaskIntervalDao) : LocalIntervalDataSource {

    // Same single-writer funnel as the other Room data sources — see projectWriteDispatcher in RoomCalls.kt.
    private val dbWriteDispatcher = platformIoDispatcher.limitedParallelism(1)

    override suspend fun upsertTaskInterval(interval: TaskInterval): EmptyResult<DataError.Local> =
        write {
            taskIntervalDao.upsertTaskInterval(interval.toTaskIntervalEntity())
        }

    override suspend fun getIntervalById(intervalId: String): Result<TaskInterval?, DataError.Local> =
        read {
            taskIntervalDao.getIntervalById(intervalId)?.toTaskInterval()
        }

    override suspend fun getOpenIntervalByTaskId(taskId: String): Result<TaskInterval?, DataError.Local> =
        read {
            taskIntervalDao.getOpenIntervalBySessionId(taskId)?.toTaskInterval()
        }

    override suspend fun deleteTaskInterval(intervalId: String): EmptyResult<DataError.Local> =
        write {
            taskIntervalDao.deleteTaskInterval(intervalId)
        }

    private inline fun <T> read(block: () -> T): Result<T, DataError.Local> =
        try {
            Result.Success(block())
        } catch (exception: SQLiteException) {
            Logger.withTag("RoomLocalIntervalDataSource").e(exception) { "read failed (SQLiteException)" }
            Result.Error(DataError.Local.UNKNOWN)
        }

    private suspend fun write(block: suspend () -> Unit): EmptyResult<DataError.Local> =
        try {
            withContext(dbWriteDispatcher) { block() }
            Result.Success(Unit)
        } catch (exception: SQLiteException) {
            Logger.withTag("RoomLocalIntervalDataSource").e(exception) { "write failed (SQLiteException)" }
            Result.Error(DataError.Local.DISK_FULL)
        }
}
