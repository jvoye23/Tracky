package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTasks
import com.jvcs.tracky.core.database.relation.TaskWithIntervals
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Upsert
    suspend fun upsertProjects(products: List<ProjectEntity>)

    @Upsert
    suspend fun upsertProject(project: ProjectEntity)

    @Query("SELECT * FROM projects ORDER BY projectId ASC")
    fun getProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE projectId = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Query("DELETE FROM projects WHERE projectId = :projectId")
    suspend fun deleteProject(projectId: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()

    @Transaction
    @Query("SELECT * FROM projects")
    fun getProjectsWithTasks(): Flow<List<ProjectWithTasks>>

    @Transaction
    @Query("SELECT * FROM projects WHERE projectId = :projectId")
    suspend fun getProjectWithTasksById(projectId: String): ProjectWithTasks?

    @Upsert
    suspend fun upsertProjectRecord(record: ProjectTaskEntity)

    @Query("DELETE FROM project_records WHERE recordId = :recordId")
    suspend fun deleteProjectRecord(recordId: String)

    @Query("UPDATE project_records SET durationMillis = :newDurationMillis WHERE recordId = :taskId")
    suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long)

    @Transaction
    @Query("SELECT * FROM project_records WHERE recordId = :taskId")
    fun getTaskWithIntervalsById(taskId: String): Flow<TaskWithIntervals?>

    @Upsert
    suspend fun upsertTaskInterval(interval: TaskIntervalEntity)

    @Query("DELETE FROM task_intervals WHERE parentTaskId = :taskId")
    suspend fun deleteIntervalsByTaskId(taskId: String)

    @Query("SELECT * FROM task_intervals WHERE parentTaskId = :sessionId AND endDateTimeUtc IS NULL LIMIT 1")
    suspend fun getOpenIntervalBySessionId(sessionId: String): TaskIntervalEntity?

    @Query("UPDATE project_records SET isTimerRunning = :isRunning WHERE recordId = :sessionId")
    suspend fun updateSessionTimerStatus(sessionId: String, isRunning: Boolean)

    @Query("UPDATE project_records SET durationMillis = durationMillis + :additionalDuration WHERE recordId = :taskId")
    suspend fun addTaskDuration(taskId: String, additionalDuration: Long)

    @Query("UPDATE project_records SET description = :title WHERE recordId = :taskId")
    suspend fun updateTaskTitle(taskId: String, title: String)
}