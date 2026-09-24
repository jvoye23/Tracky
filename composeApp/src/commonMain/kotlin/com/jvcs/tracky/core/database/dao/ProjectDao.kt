package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.relation.ProjectSortIndexEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTaskTreeEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTasksEntity
import com.jvcs.tracky.core.database.relation.SubTaskSortIndexEntity
import com.jvcs.tracky.core.database.relation.TaskSortIndexEntity
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

    // The row on its own, without the task tree: the detail screen keeps its tasks on a one-shot
    // read and only needs the project's own fields to stay live.
    @Query("SELECT * FROM projects WHERE projectId = :id")
    fun observeProjectById(id: String): Flow<ProjectEntity?>

    @Query("DELETE FROM projects WHERE projectId = :projectId")
    suspend fun deleteProject(projectId: String)

    // task_intervals and project_tasks both cascade from projects, so this clears the whole tree.
    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()

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

    @Query("SELECT projectId FROM projects WHERE trashedAtEpochMs IS NOT NULL AND trashedAtEpochMs < :cutoffEpochMs")
    suspend fun getExpiredTrashedProjectIds(cutoffEpochMs: Long): List<String>

    @Query("SELECT projectId, sortIndex FROM projects")
    suspend fun getSortIndices(): List<ProjectSortIndexEntity>

    @Query("UPDATE projects SET sortIndex = :sortIndex, updatedAtEpochMs = :updatedAt WHERE projectId = :projectId")
    suspend fun setSortIndex(
        projectId: String,
        sortIndex: Long,
        updatedAt: Long,
    )

    // A reorder is one gesture, so it is one write: either every index lands or none does. Doing it
    // row by row outside a transaction can leave two projects sharing an index if one write fails.
    @Transaction
    suspend fun updateSortIndices(indices: Map<String, Long>, updatedAt: Long) {
        indices.forEach { (id, index) -> setSortIndex(id, index, updatedAt) }
    }

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

    // Task order is per project, so unlike the project queries these are scoped to one parent.
    @Query("SELECT projectTaskId, sortIndex FROM project_tasks WHERE parentProjectId = :projectId")
    suspend fun getTaskSortIndices(projectId: String): List<TaskSortIndexEntity>

    @Query(
        "UPDATE project_tasks SET sortIndex = :sortIndex, updatedAtEpochMs = :updatedAt WHERE projectTaskId = :taskId",
    )
    suspend fun setTaskSortIndex(
        taskId: String,
        sortIndex: Long,
        updatedAt: Long,
    )

    // One gesture, one write — see updateSortIndices for why this has to be transactional.
    @Transaction
    suspend fun updateTaskSortIndices(indices: Map<String, Long>, updatedAt: Long) {
        indices.forEach { (id, index) -> setTaskSortIndex(id, index, updatedAt) }
    }

    // Subtask order is per task, one level further down than the task queries above.
    @Query("SELECT projectSubTaskId, sortIndex FROM project_sub_tasks WHERE parentProjectTaskId = :taskId")
    suspend fun getSubTaskSortIndices(taskId: String): List<SubTaskSortIndexEntity>

    @Query(
        "UPDATE project_sub_tasks SET sortIndex = :sortIndex, updatedAtEpochMs = :updatedAt WHERE projectSubTaskId = :subTaskId",
    )
    suspend fun setSubTaskSortIndex(
        subTaskId: String,
        sortIndex: Long,
        updatedAt: Long,
    )

    // One gesture, one write — see updateSortIndices for why this has to be transactional.
    @Transaction
    suspend fun updateSubTaskSortIndices(indices: Map<String, Long>, updatedAt: Long) {
        indices.forEach { (id, index) -> setSubTaskSortIndex(id, index, updatedAt) }
    }
}
