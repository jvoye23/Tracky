package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.jvcs.tracky.core.database.relation.ProjectWithTaskTreeEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTasksEntity
import kotlinx.coroutines.flow.Flow

/** Projects read together with the rows under them. */
@Dao
interface ProjectTreeDao {

    @Transaction
    @Query("SELECT * FROM projects")
    fun getProjectsWithTasks(): Flow<List<ProjectWithTasksEntity>>

    @Transaction
    @Query("SELECT * FROM projects WHERE isArchived = 0 AND isFinished = 0 AND trashedAtEpochMs IS NULL")
    fun getActiveProjectsWithTasks(): Flow<List<ProjectWithTasksEntity>>

    @Query("SELECT * FROM projects WHERE isArchived = 1 AND trashedAtEpochMs IS NULL ORDER BY projectId ASC")
    fun getArchivedProjectsWithTasks(): Flow<List<ProjectWithTasksEntity>>

    @Transaction
    @Query("SELECT * FROM projects WHERE trashedAtEpochMs IS NOT NULL ORDER BY trashedAtEpochMs DESC")
    fun getTrashedProjectsWithTasks(): Flow<List<ProjectWithTasksEntity>>

    @Query(
        "SELECT * FROM projects WHERE isArchived = 0 AND isFinished = 0 AND trashedAtEpochMs IS NULL AND isPinned = 1",
    )
    fun getPinnedProjectsWithTasks(): Flow<List<ProjectWithTasksEntity>>

    @Transaction
    @Query("SELECT * FROM projects WHERE projectId = :projectId")
    suspend fun getProjectWithTasksById(projectId: String): ProjectWithTasksEntity?

    /** The detail-screen read: tasks arrive hydrated with their intervals and subtasks. */
    @Transaction
    @Query("SELECT * FROM projects WHERE projectId = :projectId")
    suspend fun getProjectWithTaskTreeById(projectId: String): ProjectWithTaskTreeEntity?

    /**
     * The same tree, streamed. A sync writes another device's rows straight into these tables, so
     * the detail screen has to hear about it the way every other screen does — by observing Room
     * rather than by re-reading on a trigger someone has to remember to fire.
     */
    @Transaction
    @Query("SELECT * FROM projects WHERE projectId = :projectId")
    fun observeProjectWithTaskTreeById(projectId: String): Flow<ProjectWithTaskTreeEntity?>
}
