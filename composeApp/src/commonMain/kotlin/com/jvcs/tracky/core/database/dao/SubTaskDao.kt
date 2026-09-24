package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.relation.SubTaskWithIntervals
import kotlinx.coroutines.flow.Flow

/**
 * A subtask row: its own writes, its timer flag and banked total, and its hydrated reads.
 *
 * The reads mirror their task-level counterparts in TaskDao so the two levels stay swappable.
 */
@Dao
interface SubTaskDao {

    @Upsert
    suspend fun upsertProjectSubTask(subTask: ProjectSubTaskEntity)

    @Transaction
    @Query("SELECT * FROM project_sub_tasks WHERE parentProjectTaskId = :taskId")
    fun getSubTasksWithIntervals(taskId: String): Flow<List<SubTaskWithIntervals>>

    @Query("SELECT * FROM project_sub_tasks WHERE projectSubTaskId = :subTaskId")
    suspend fun getSubTaskById(subTaskId: String): ProjectSubTaskEntity?

    @Query("DELETE FROM project_sub_tasks WHERE projectSubTaskId = :subTaskId")
    suspend fun deleteProjectSubTask(subTaskId: String)

    @Query("UPDATE project_sub_tasks SET isTimerRunning = :isRunning WHERE projectSubTaskId = :subTaskId")
    suspend fun updateSubTaskTimerStatus(subTaskId: String, isRunning: Boolean)

    @Query(
        "UPDATE project_sub_tasks SET durationMillis = COALESCE(durationMillis, 0) + :additionalDuration WHERE projectSubTaskId = :subTaskId",
    )
    suspend fun addSubTaskDuration(subTaskId: String, additionalDuration: Long)

    /** The subtask twin of [getBankedTaskDuration]; `parentSubTaskId` is indexed too. */
    @Query(
        "SELECT COALESCE(SUM(durationMillis), 0) FROM sub_task_intervals " +
            "WHERE parentSubTaskId = :subTaskId AND endDateTimeEpochMs IS NOT NULL",
    )
    suspend fun getBankedSubTaskDuration(subTaskId: String): Long

    // Whether a task-level parked interval is worth keeping at all: a task that owns subtasks is
    // counted through them, so time banked on the task itself renders nowhere. See StrandedTimer.
    @Query("SELECT COUNT(*) FROM project_sub_tasks WHERE parentProjectTaskId = :taskId")
    suspend fun countSubTasks(taskId: String): Int

    // Backs the parent task's play button, which resumes whatever was worked on last rather than
    // opening a task-level interval of its own. Same join as
    // SubTaskIntervalDao.getOpenSubTaskIntervalForTask; ordered instead of filtered.
    @Query(
        "SELECT si.parentSubTaskId FROM sub_task_intervals AS si " +
            "JOIN project_sub_tasks AS s ON s.projectSubTaskId = si.parentSubTaskId " +
            "WHERE s.parentProjectTaskId = :taskId " +
            "ORDER BY si.startDateTimeEpochMs DESC LIMIT 1",
    )
    suspend fun getLastStartedSubTaskId(taskId: String): String?
}
