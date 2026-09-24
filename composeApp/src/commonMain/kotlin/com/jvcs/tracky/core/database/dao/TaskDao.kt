package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.relation.TaskWithIntervals
import com.jvcs.tracky.core.database.relation.TaskWithSubTasks
import kotlinx.coroutines.flow.Flow

/** A task row: its own writes, its timer flag and banked total, and its hydrated reads. */
@Dao
interface TaskDao {

    @Upsert
    suspend fun upsertProjectTask(task: ProjectTaskEntity)

    @Query("SELECT * FROM project_tasks WHERE projectTaskId = :projectTaskId")
    suspend fun getTaskById(projectTaskId: String): ProjectTaskEntity?

    @Query("DELETE FROM project_tasks WHERE projectTaskId = :projectTaskId")
    suspend fun deleteProjectTask(projectTaskId: String)

    @Query("UPDATE project_tasks SET durationMillis = :newDurationMillis WHERE projectTaskId = :taskId")
    suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long)

    @Transaction
    @Query("SELECT * FROM project_tasks WHERE projectTaskId = :taskId")
    fun getTaskWithIntervalsById(taskId: String): Flow<TaskWithIntervals?>

    @Transaction
    @Query("SELECT * FROM project_tasks WHERE projectTaskId = :taskId")
    fun getTaskWithSubTasksById(taskId: String): Flow<TaskWithSubTasks?>

    @Query("UPDATE project_tasks SET isTimerRunning = :isRunning WHERE projectTaskId = :sessionId")
    suspend fun updateSessionTimerStatus(sessionId: String, isRunning: Boolean)

    @Query(
        "UPDATE project_tasks SET durationMillis = durationMillis + :additionalDuration WHERE projectTaskId = :taskId",
    )
    suspend fun addTaskDuration(taskId: String, additionalDuration: Long)

    @Query("UPDATE project_tasks SET title = :title WHERE projectTaskId = :taskId")
    suspend fun updateTaskTitle(taskId: String, title: String)

    /**
     * What a task has already banked, summed from its own closed intervals.
     *
     * Not `project_tasks.durationMillis`, which is a running total maintained by whichever device
     * did the stopping. A device that has just adopted a timer another device started may not have
     * pulled that total yet, so reading it would show the wrong number until it does — and would
     * keep showing it if the task row's last-write-wins ever went the other way.
     *
     * The sum cannot disagree with the interval table, and it converges the instant the closing
     * interval arrives. `parentTaskId` is indexed, so the cost is one indexed aggregate per
     * emission of the running timer.
     */
    @Query(
        "SELECT COALESCE(SUM(durationMillis), 0) FROM task_intervals " +
            "WHERE parentTaskId = :taskId AND endDateTimeEpochMs IS NOT NULL",
    )
    suspend fun getBankedTaskDuration(taskId: String): Long
}
