package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * The projects table's own rows. Reads that bring the tasks along are in ProjectTreeDao, and the
 * manual order in SortOrderDao.
 */
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

    // The row on its own, without the task tree: the detail screen keeps its tasks on a one-shot
    // read and only needs the project's own fields to stay live.
    @Query("SELECT * FROM projects WHERE projectId = :id")
    fun observeProjectById(id: String): Flow<ProjectEntity?>

    @Query("DELETE FROM projects WHERE projectId = :projectId")
    suspend fun deleteProject(projectId: String)

    // task_intervals and project_tasks both cascade from projects, so this clears the whole tree.
    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()

    @Query("SELECT projectId FROM projects WHERE trashedAtEpochMs IS NOT NULL AND trashedAtEpochMs < :cutoffEpochMs")
    suspend fun getExpiredTrashedProjectIds(cutoffEpochMs: Long): List<String>
}
