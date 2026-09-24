package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.jvcs.tracky.core.database.relation.ProjectSortIndexEntity
import com.jvcs.tracky.core.database.relation.SubTaskSortIndexEntity
import com.jvcs.tracky.core.database.relation.TaskSortIndexEntity

/** The persisted manual order of projects, tasks and subtasks, one level at a time. */
@Dao
interface SortOrderDao {

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
