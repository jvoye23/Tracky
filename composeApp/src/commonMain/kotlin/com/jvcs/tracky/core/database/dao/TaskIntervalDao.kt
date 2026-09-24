package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import kotlinx.coroutines.flow.Flow

/** Task-level intervals: the rows, the open-interval lookups the timer runs on, and the reconciler's input. */
@Dao
interface TaskIntervalDao {

    @Upsert
    suspend fun upsertTaskInterval(interval: TaskIntervalEntity)

    // Needed by the pending-sync drain: a queued interval op stores only the interval id, so the
    // row has to be re-read from local state when it is finally pushed.
    @Query("SELECT * FROM task_intervals WHERE intervalId = :intervalId")
    suspend fun getIntervalById(intervalId: String): TaskIntervalEntity?

    @Query("DELETE FROM task_intervals WHERE intervalId = :intervalId")
    suspend fun deleteTaskInterval(intervalId: String)

    // More than one open interval per task is a bug (see startTask's reuse guard), but the rows can
    // already exist on a device that ran an older build, and LIMIT 1 without an order leaves which
    // one comes back to the query planner. Newest-first so a stop closes the interval the user just
    // started, never a stranded one whose span covers the days since.
    @Query(
        "SELECT * FROM task_intervals WHERE parentTaskId = :sessionId AND endDateTimeEpochMs IS NULL " +
            "AND intervalId NOT IN (SELECT intervalId FROM stranded_intervals) " +
            "ORDER BY startDateTimeEpochMs DESC LIMIT 1",
    )
    suspend fun getOpenIntervalBySessionId(sessionId: String): TaskIntervalEntity?

    // The running timer's two inputs (this and SubTaskIntervalDao.observeOpenSubTaskInterval),
    // mirroring the reconciler's pair below but filtered the other way: parked rows are open and
    // timing nothing, so they must never look like a running timer. Global rather than per-task -
    // only one timer runs at a time - and newest-first for the same reason getOpenIntervalBySessionId
    // is, so a device carrying stale open rows from an older build reports the one the user just
    // started.
    @Query(
        "SELECT * FROM task_intervals WHERE endDateTimeEpochMs IS NULL " +
            "AND intervalId NOT IN (SELECT intervalId FROM stranded_intervals) " +
            "ORDER BY startDateTimeEpochMs DESC LIMIT 1",
    )
    fun observeOpenTaskInterval(): Flow<TaskIntervalEntity?>

    // The reconciler's two inputs.
    //
    // Not filtered against stranded_intervals, on purpose: the reconciler is the thing that decides
    // what counts as stranded, so it has to see rows it has already flagged to stay idempotent.
    //
    // Filtered by device, also on purpose: an open interval another device started is a timer the
    // user is running right now, not wreckage from a crash here. A NULL id predates multi-device
    // sync and means this device, which is what those rows have always meant.
    @Query(
        "SELECT * FROM task_intervals WHERE endDateTimeEpochMs IS NULL " +
            "AND (startedByDeviceId IS NULL OR startedByDeviceId = :deviceId)",
    )
    suspend fun getAllOpenTaskIntervalsForDevice(deviceId: String): List<TaskIntervalEntity>
}
