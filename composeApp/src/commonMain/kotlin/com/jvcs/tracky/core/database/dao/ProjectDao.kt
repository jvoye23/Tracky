package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSessionEntity
import com.jvcs.tracky.core.database.entity.SessionIntervalEntity
import com.jvcs.tracky.core.database.relation.ProjectWithSessions
import com.jvcs.tracky.core.database.relation.SessionWithIntervals
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
    fun getProjectsWithSessions(): Flow<List<ProjectWithSessions>>

    @Transaction
    @Query("SELECT * FROM projects WHERE projectId = :projectId")
    suspend fun getProjectWithSessionsById(projectId: String): ProjectWithSessions?

    @Upsert
    suspend fun upsertProjectRecord(record: ProjectSessionEntity)

    @Query("DELETE FROM project_records WHERE recordId = :recordId")
    suspend fun deleteProjectRecord(recordId: String)

    @Query("UPDATE project_records SET durationMillis = :newDurationMillis WHERE recordId = :sessionId")
    suspend fun updateSessionDuration(sessionId: String, newDurationMillis: Long)

    @Transaction
    @Query("SELECT * FROM project_records WHERE recordId = :sessionId")
    fun getSessionWithIntervalsById(sessionId: String): Flow<SessionWithIntervals?>

    @Upsert
    suspend fun upsertSessionInterval(interval: SessionIntervalEntity)

    @Query("DELETE FROM session_intervals WHERE parentSessionId = :sessionId")
    suspend fun deleteIntervalsBySessionId(sessionId: String)

    @Query("SELECT * FROM session_intervals WHERE parentSessionId = :sessionId AND endDateTimeEpochMs IS NULL LIMIT 1")
    suspend fun getOpenIntervalBySessionId(sessionId: String): SessionIntervalEntity?

    @Query("UPDATE project_records SET isTimerRunning = :isRunning WHERE recordId = :sessionId")
    suspend fun updateSessionTimerStatus(sessionId: String, isRunning: Boolean)

    @Query("UPDATE project_records SET durationMillis = durationMillis + :additionalDuration WHERE recordId = :sessionId")
    suspend fun addSessionDuration(sessionId: String, additionalDuration: Long)

    @Query("UPDATE project_records SET description = :title WHERE recordId = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, title: String)
}