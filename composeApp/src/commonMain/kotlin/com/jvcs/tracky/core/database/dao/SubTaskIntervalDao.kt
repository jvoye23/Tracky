package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import kotlinx.coroutines.flow.Flow

/** Subtask-level intervals: the rows, the open-interval lookups the timer runs on, and the reconciler's input. */
@Dao
interface SubTaskIntervalDao {

    @Upsert
    suspend fun upsertSubTaskInterval(interval: SubTaskIntervalEntity)

    @Query("SELECT * FROM sub_task_intervals WHERE subTaskIntervalId = :subTaskIntervalId")
    suspend fun getSubTaskIntervalById(subTaskIntervalId: String): SubTaskIntervalEntity?

    @Query("DELETE FROM sub_task_intervals WHERE subTaskIntervalId = :subTaskIntervalId")
    suspend fun deleteSubTaskInterval(subTaskIntervalId: String)

    // The open-interval lookup the timer needs, mirroring getOpenIntervalBySessionId. At most one
    // row can come back: a subtask has one timer, and closing it stamps endDateTimeEpochMs.
    @Query(
        "SELECT * FROM sub_task_intervals WHERE parentSubTaskId = :subTaskId AND endDateTimeEpochMs IS NULL " +
            "AND subTaskIntervalId NOT IN (SELECT intervalId FROM stranded_intervals) " +
            "ORDER BY startDateTimeEpochMs DESC LIMIT 1",
    )
    suspend fun getOpenSubTaskInterval(subTaskId: String): SubTaskIntervalEntity?

    // Only one subtask under a task may run at a time, so starting one has to find whichever
    // sibling is currently open and close it. The join is what makes "sibling" mean "under the
    // same task" rather than "under the same subtask".
    @Query(
        "SELECT si.* FROM sub_task_intervals AS si " +
            "JOIN project_sub_tasks AS s ON s.projectSubTaskId = si.parentSubTaskId " +
            "WHERE s.parentProjectTaskId = :taskId AND si.endDateTimeEpochMs IS NULL " +
            "AND si.subTaskIntervalId NOT IN (SELECT intervalId FROM stranded_intervals) " +
            "ORDER BY si.startDateTimeEpochMs DESC LIMIT 1",
    )
    suspend fun getOpenSubTaskIntervalForTask(taskId: String): SubTaskIntervalEntity?

    // Backs the parent task's play button, which resumes whatever was worked on last rather than
    // opening a task-level interval of its own. Same join as above; ordered instead of filtered.
    // The subtask half of the running timer's inputs; see TaskIntervalDao.observeOpenTaskInterval.
    @Query(
        "SELECT * FROM sub_task_intervals WHERE endDateTimeEpochMs IS NULL " +
            "AND subTaskIntervalId NOT IN (SELECT intervalId FROM stranded_intervals) " +
            "ORDER BY startDateTimeEpochMs DESC LIMIT 1",
    )
    fun observeOpenSubTaskInterval(): Flow<SubTaskIntervalEntity?>

    @Query(
        "SELECT * FROM sub_task_intervals WHERE endDateTimeEpochMs IS NULL " +
            "AND (startedByDeviceId IS NULL OR startedByDeviceId = :deviceId)",
    )
    suspend fun getAllOpenSubTaskIntervalsForDevice(deviceId: String): List<SubTaskIntervalEntity>
}
