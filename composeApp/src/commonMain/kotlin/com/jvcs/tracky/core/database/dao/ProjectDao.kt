package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.database.relation.ProjectSortIndexEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTaskTreeEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTasksEntity
import com.jvcs.tracky.core.database.relation.SubTaskWithIntervals
import com.jvcs.tracky.core.database.relation.TaskWithIntervals
import com.jvcs.tracky.core.database.relation.TaskWithSubTasks
import com.jvcs.tracky.core.domain.sync.serverWinsOnPull
import com.jvcs.tracky.core.domain.sync.serverWinsOnPullForInterval
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Upsert
    suspend fun upsertProjects(products: List<ProjectEntity>)

    @Upsert
    suspend fun upsertProject(project: ProjectEntity)

    /**
     * Writes a whole server tree (projects, their tasks, those tasks' intervals and subtasks) in
     * one transaction.
     *
     * `GET /api/projects` returns everything the user owns, so this is what rehydrates a fresh
     * install. Rows are merged rather than blindly overwritten — see [serverWinsOnPull] — and
     * nothing is ever deleted: a local row the server does not know about is either still queued
     * for upload or was created offline, and must survive the pull either way.
     *
     * Rows are written parents-first because Room enforces the foreign keys, and a row whose parent
     * is absent is skipped rather than inserted: one dangling reference throws inside the
     * transaction and would lose the *entire* pull, not just that row. The three oldest levels need
     * no such filter only because their parent is always in the same payload; subtasks and their
     * intervals do, and a subtask interval has to clear *both* of its parents.
     */
    @Transaction
    suspend fun upsertServerTree(
        projects: List<ProjectEntity>,
        tasks: List<ProjectTaskEntity>,
        intervals: List<TaskIntervalEntity>,
        subTasks: List<ProjectSubTaskEntity> = emptyList(),
        subTaskIntervals: List<SubTaskIntervalEntity> = emptyList()
    ) {
        projects.forEach { incoming ->
            val local = getProjectById(incoming.projectId)
            if (serverWinsOnPull(local?.updatedAtEpochMs, incoming.updatedAtEpochMs)) {
                upsertProject(incoming)
            }
        }
        tasks.forEach { incoming ->
            val local = getTaskById(incoming.projectTaskId)
            if (serverWinsOnPull(local?.updatedAtEpochMs, incoming.updatedAtEpochMs)) {
                upsertProjectTask(incoming)
            }
        }
        intervals.forEach { incoming ->
            val local = getIntervalById(incoming.intervalId)
            if (local == null || serverWinsOnPullForInterval(local.endDateTimeEpochMs)) {
                upsertTaskInterval(incoming)
            }
        }
        subTasks.forEach { incoming ->
            if (getTaskById(incoming.parentProjectTaskId) == null) return@forEach
            val local = getSubTaskById(incoming.projectSubTaskId)
            // A real stamp, exactly like a task's: subtasks are edited by hand.
            if (serverWinsOnPull(local?.updatedAtEpochMs, incoming.updatedAtEpochMs)) {
                upsertProjectSubTask(incoming)
            }
        }
        subTaskIntervals.forEach { incoming ->
            // Two cascading parents, so two ways to dangle.
            if (getSubTaskById(incoming.parentSubTaskId) == null) return@forEach
            if (getIntervalById(incoming.parentTaskIntervalId) == null) return@forEach
            val local = getSubTaskIntervalById(incoming.subTaskIntervalId)
            if (local == null || serverWinsOnPullForInterval(local.endDateTimeEpochMs)) {
                // startedParentTimer has no wire counterpart, so the server's copy is always false.
                // Keeping the local value is what preserves "stopping this subtask also stops its
                // parent task" across a pull. A row this device has never seen gets false, which is
                // safe: the merge only lets the server win when the local row is already closed,
                // and the flag is only ever read off an open one.
                upsertSubTaskInterval(
                    incoming.copy(startedParentTimer = local?.startedParentTimer ?: false)
                )
            }
        }
    }

    @Query("SELECT * FROM projects ORDER BY projectId ASC")
    fun getProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE projectId = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

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

    @Query("SELECT * FROM projects WHERE isArchived = 0 AND isFinished = 0 AND trashedAtEpochMs IS NULL AND isPinned = 1")
    fun getPinnedProjectsWithTasks(): Flow<List<ProjectWithTasksEntity>>

    @Query("SELECT projectId FROM projects WHERE trashedAtEpochMs IS NOT NULL AND trashedAtEpochMs < :cutoffEpochMs")
    suspend fun getExpiredTrashedProjectIds(cutoffEpochMs: Long): List<String>

    @Query("SELECT projectId, sortIndex FROM projects")
    suspend fun getSortIndices(): List<ProjectSortIndexEntity>

    @Query("UPDATE projects SET sortIndex = :sortIndex, updatedAtEpochMs = :updatedAt WHERE projectId = :projectId")
    suspend fun setSortIndex(projectId: String, sortIndex: Long, updatedAt: Long)

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

    @Upsert
    suspend fun upsertTaskInterval(interval: TaskIntervalEntity)

    // Needed by the pending-sync drain: a queued interval op stores only the interval id, so the
    // row has to be re-read from local state when it is finally pushed.
    @Query("SELECT * FROM task_intervals WHERE intervalId = :intervalId")
    suspend fun getIntervalById(intervalId: String): TaskIntervalEntity?

    @Query("DELETE FROM task_intervals WHERE intervalId = :intervalId")
    suspend fun deleteTaskInterval(intervalId: String)

    @Query("SELECT * FROM task_intervals WHERE parentTaskId = :sessionId AND endDateTimeEpochMs IS NULL LIMIT 1")
    suspend fun getOpenIntervalBySessionId(sessionId: String): TaskIntervalEntity?

    @Query("UPDATE project_tasks SET isTimerRunning = :isRunning WHERE projectTaskId = :sessionId")
    suspend fun updateSessionTimerStatus(sessionId: String, isRunning: Boolean)

    @Query("UPDATE project_tasks SET durationMillis = durationMillis + :additionalDuration WHERE projectTaskId = :taskId")
    suspend fun addTaskDuration(taskId: String, additionalDuration: Long)

    @Query("UPDATE project_tasks SET title = :title WHERE projectTaskId = :taskId")
    suspend fun updateTaskTitle(taskId: String, title: String)
    // ---- Subtasks ---------------------------------------------------------------------------
    // Local-only for now: the backend exposes no subtask routes, so nothing here feeds the pending
    // sync queue. The reads mirror their task-level counterparts so the two levels stay swappable.

    @Upsert
    suspend fun upsertProjectSubTask(subTask: ProjectSubTaskEntity)

    @Transaction
    @Query("SELECT * FROM project_tasks WHERE projectTaskId = :taskId")
    fun getTaskWithSubTasksById(taskId: String): Flow<TaskWithSubTasks?>

    @Transaction
    @Query("SELECT * FROM project_sub_tasks WHERE parentProjectTaskId = :taskId")
    fun getSubTasksWithIntervals(taskId: String): Flow<List<SubTaskWithIntervals>>

    @Query("SELECT * FROM project_sub_tasks WHERE projectSubTaskId = :subTaskId")
    suspend fun getSubTaskById(subTaskId: String): ProjectSubTaskEntity?

    @Query("DELETE FROM project_sub_tasks WHERE projectSubTaskId = :subTaskId")
    suspend fun deleteProjectSubTask(subTaskId: String)

    @Query("UPDATE project_sub_tasks SET isTimerRunning = :isRunning WHERE projectSubTaskId = :subTaskId")
    suspend fun updateSubTaskTimerStatus(subTaskId: String, isRunning: Boolean)

    @Query("UPDATE project_sub_tasks SET durationMillis = COALESCE(durationMillis, 0) + :additionalDuration WHERE projectSubTaskId = :subTaskId")
    suspend fun addSubTaskDuration(subTaskId: String, additionalDuration: Long)

    @Upsert
    suspend fun upsertSubTaskInterval(interval: SubTaskIntervalEntity)

    @Query("SELECT * FROM sub_task_intervals WHERE subTaskIntervalId = :subTaskIntervalId")
    suspend fun getSubTaskIntervalById(subTaskIntervalId: String): SubTaskIntervalEntity?

    @Query("DELETE FROM sub_task_intervals WHERE subTaskIntervalId = :subTaskIntervalId")
    suspend fun deleteSubTaskInterval(subTaskIntervalId: String)

    // The open-interval lookup the timer needs, mirroring getOpenIntervalBySessionId. At most one
    // row can come back: a subtask has one timer, and closing it stamps endDateTimeEpochMs.
    @Query("SELECT * FROM sub_task_intervals WHERE parentSubTaskId = :subTaskId AND endDateTimeEpochMs IS NULL LIMIT 1")
    suspend fun getOpenSubTaskInterval(subTaskId: String): SubTaskIntervalEntity?

    // Only one subtask under a task may run at a time, so starting one has to find whichever
    // sibling is currently open and close it. The join is what makes "sibling" mean "under the
    // same task" rather than "under the same subtask".
    @Query(
        "SELECT si.* FROM sub_task_intervals AS si " +
            "JOIN project_sub_tasks AS s ON s.projectSubTaskId = si.parentSubTaskId " +
            "WHERE s.parentProjectTaskId = :taskId AND si.endDateTimeEpochMs IS NULL LIMIT 1"
    )
    suspend fun getOpenSubTaskIntervalForTask(taskId: String): SubTaskIntervalEntity?

    // Backs the parent task's play button, which resumes whatever was worked on last rather than
    // opening a task-level interval of its own. Same join as above; ordered instead of filtered.
    @Query(
        "SELECT si.parentSubTaskId FROM sub_task_intervals AS si " +
            "JOIN project_sub_tasks AS s ON s.projectSubTaskId = si.parentSubTaskId " +
            "WHERE s.parentProjectTaskId = :taskId " +
            "ORDER BY si.startDateTimeEpochMs DESC LIMIT 1"
    )
    suspend fun getLastStartedSubTaskId(taskId: String): String?
}
