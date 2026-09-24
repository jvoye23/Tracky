package com.jvcs.tracky.features.project.data.project

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.platformIoDispatcher
import kotlinx.coroutines.withContext

/**
 * Writes are funnelled through a single-threaded dispatcher. The reactive sync does bulk writes
 * on the application scope while the timer writes intervals; letting those interleave across
 * connections is what corrupted the WAL file (SQLITE_NOTADB).
 *
 * One funnel for every project data source: the rows, their organisation and the server-tree
 * writes were one class, and one funnel, before they were split.
 */
private val projectWriteDispatcher = platformIoDispatcher.limitedParallelism(1)

/** Reads run on the caller's context; Room already moves the query off the main thread. */
internal inline fun <T> roomRead(tag: String, block: () -> T): Result<T, DataError.Local> =
    try {
        Result.Success(block())
    } catch (exception: SQLiteException) {
        Logger.withTag(tag).e(exception) { "read failed (SQLiteException)" }
        Result.Error(DataError.Local.UNKNOWN)
    }

internal suspend fun roomWrite(tag: String, block: suspend () -> Unit): EmptyResult<DataError.Local> =
    try {
        withContext(projectWriteDispatcher) { block() }
        Result.Success(Unit)
    } catch (exception: SQLiteException) {
        Logger.withTag(tag).e(exception) { "write failed (SQLiteException)" }
        Result.Error(DataError.Local.DISK_FULL)
    }
