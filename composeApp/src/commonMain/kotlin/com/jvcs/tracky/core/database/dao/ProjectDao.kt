package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSessionEntity
import com.jvcs.tracky.core.database.relation.ProjectWithSessions
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
    fun getProjectWithSessionsById(projectId: String): ProjectWithSessions?

    @Upsert
    suspend fun upsertProjectRecord(record: ProjectSessionEntity)

    @Query("DELETE FROM project_records WHERE recordId = :recordId")
    suspend fun deleteProjectRecord(recordId: String)

    @Query("UPDATE project_records SET durationMillis = :newDurationMillis WHERE recordId = :sessionId")
    suspend fun updateSessionDuration(sessionId: String, newDurationMillis: Long)
}