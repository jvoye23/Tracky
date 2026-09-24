package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.StrandedIntervalEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.database.relation.ProjectSortIndexEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTaskTreeEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTasksEntity
import com.jvcs.tracky.core.database.relation.SubTaskSortIndexEntity
import com.jvcs.tracky.core.database.relation.SubTaskWithIntervals
import com.jvcs.tracky.core.database.relation.TaskSortIndexEntity
import com.jvcs.tracky.core.database.relation.TaskWithIntervals
import com.jvcs.tracky.core.database.relation.TaskWithSubTasks
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

    // More than one open interval per task is a bug (see startTask's reuse guard), but the rows can
    // already exist on a device that ran an older build, and LIMIT 1 without an order leaves which
    // one comes back to the query planner. Newest-first so a stop closes the interval the user just
    // started, never a stranded one whose span covers the days since.
    @Query(
        "SELECT * FROM task_intervals WHERE parentTaskId = :sessionId AND endDateTimeEpochMs IS NULL " +
            "AND intervalId NOT IN (SELECT intervalId FROM stranded_intervals) " +
            "ORDER BY startDateTimeEpochMs DESC LIMIT 1",
    )
    suspend fun getOpenIntervalBySessionId(sessionId: String): TaskIntervalEntity?

    @Query("UPDATE project_tasks SET isTimerRunning = :isRunning WHERE projectTaskId = :sessionId")
    suspend fun updateSessionTimerStatus(sessionId: String, isRunning: Boolean)

    @Query(
        "UPDATE project_tasks SET durationMillis = durationMillis + :additionalDuration WHERE projectTaskId = :taskId",
    )
    suspend fun addTaskDuration(taskId: String, additionalDuration: Long)

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

    /** The subtask twin of [getBankedTaskDuration]; `parentSubTaskId` is indexed too. */
    @Query(
        "SELECT COALESCE(SUM(durationMillis), 0) FROM sub_task_intervals " +
            "WHERE parentSubTaskId = :subTaskId AND endDateTimeEpochMs IS NOT NULL",
    )
    suspend fun getBankedSubTaskDuration(subTaskId: String): Long

    @Query("UPDATE project_tasks SET title = :title WHERE projectTaskId = :taskId")
    suspend fun updateTaskTitle(taskId: String, title: String)

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
    // ---- Subtasks ---------------------------------------------------------------------------
    // The reads mirror their task-level counterparts so the two levels stay swappable.

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

    @Query(
        "UPDATE project_sub_tasks SET durationMillis = COALESCE(durationMillis, 0) + :additionalDuration WHERE projectSubTaskId = :subTaskId",
    )
    suspend fun addSubTaskDuration(subTaskId: String, additionalDuration: Long)

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

    @Upsert
    suspend fun upsertSubTaskInterval(interval: SubTaskIntervalEntity)

    @Query("SELECT * FROM sub_task_intervals WHERE subTaskIntervalId = :subTaskIntervalId")
    suspend fun getSubTaskIntervalById(subTaskIntervalId: String): SubTaskIntervalEntity?

    @Query("DELETE FROM sub_task_intervals WHERE subTaskIntervalId = :subTaskIntervalId")
    suspend fun deleteSubTaskInterval(subTaskIntervalId: String)

    // The open-interval lookup the timer needs, mirroring getOpenIntervalBySessionId. At most one
    // row can come back: a subtask has one timer, and closing it stamps endDateTimeEpochMs.
    @Query(
        "SELECT * FROM sub_task_intervals WHERE parentSubTaskId = :subTaskId AND endDateTimeEpochMs IS NULL " +
            "AND subTaskIntervalId NOT IN (SELECT intervalId FROM stranded_intervals) " +
            "ORDER BY startDateTimeEpochMs DESC LIMIT 1",
    )
    suspend fun getOpenSubTaskInterval(subTaskId: String): SubTaskIntervalEntity?

    // Only one subtask under a task may run at a time, so starting one has to find whichever
    // sibling is currently open and close it. The join is what makes "sibling" mean "under the
    // same task" rather than "under the same subtask".
    @Query(
        "SELECT si.* FROM sub_task_intervals AS si " +
            "JOIN project_sub_tasks AS s ON s.projectSubTaskId = si.parentSubTaskId " +
            "WHERE s.parentProjectTaskId = :taskId AND si.endDateTimeEpochMs IS NULL " +
            "AND si.subTaskIntervalId NOT IN (SELECT intervalId FROM stranded_intervals) " +
            "ORDER BY si.startDateTimeEpochMs DESC LIMIT 1",
    )
    suspend fun getOpenSubTaskIntervalForTask(taskId: String): SubTaskIntervalEntity?

    // Backs the parent task's play button, which resumes whatever was worked on last rather than
    // opening a task-level interval of its own. Same join as above; ordered instead of filtered.
    // ---- Stranded intervals -----------------------------------------------------------------
    // Local-only, never synced. An interval listed here is open but nothing is timing it, so every
    // "what is currently open" query above excludes it and no aggregation counts it - an open
    // interval banks nothing until it closes. See StrandedIntervalEntity.

    @Upsert
    suspend fun upsertStrandedInterval(stranded: StrandedIntervalEntity)

    @Query("DELETE FROM stranded_intervals WHERE intervalId = :intervalId")
    suspend fun deleteStrandedInterval(intervalId: String)

    @Query("SELECT * FROM stranded_intervals WHERE intervalId = :intervalId")
    suspend fun getStrandedInterval(intervalId: String): StrandedIntervalEntity?

    @Query("SELECT * FROM stranded_intervals ORDER BY detectedAtEpochMs ASC")
    fun observeStrandedIntervals(): Flow<List<StrandedIntervalEntity>>

    // The running timer's two inputs, mirroring the reconciler's pair below but filtered the other
    // way: parked rows are open and timing nothing, so they must never look like a running timer.
    // Global rather than per-task - only one timer runs at a time - and newest-first for the same
    // reason getOpenIntervalBySessionId is, so a device carrying stale open rows from an older
    // build reports the one the user just started.

    @Query(
        "SELECT * FROM task_intervals WHERE endDateTimeEpochMs IS NULL " +
            "AND intervalId NOT IN (SELECT intervalId FROM stranded_intervals) " +
            "ORDER BY startDateTimeEpochMs DESC LIMIT 1",
    )
    fun observeOpenTaskInterval(): Flow<TaskIntervalEntity?>

    @Query(
        "SELECT * FROM sub_task_intervals WHERE endDateTimeEpochMs IS NULL " +
            "AND subTaskIntervalId NOT IN (SELECT intervalId FROM stranded_intervals) " +
            "ORDER BY startDateTimeEpochMs DESC LIMIT 1",
    )
    fun observeOpenSubTaskInterval(): Flow<SubTaskIntervalEntity?>

    // The reconciler's two inputs.
    //
    // Not filtered against stranded_intervals, on purpose: the reconciler is the thing that decides
    // what counts as stranded, so it has to see rows it has already flagged to stay idempotent.
    //
    // Filtered by device, also on purpose: an open interval another device started is a timer the
    // user is running right now, not wreckage from a crash here. A NULL id predates multi-device
    // sync and means this device, which is what those rows have always meant.
    @Query(
        "SELECT * FROM task_intervals WHERE endDateTimeEpochMs IS NULL " +
            "AND (startedByDeviceId IS NULL OR startedByDeviceId = :deviceId)",
    )
    suspend fun getAllOpenTaskIntervalsForDevice(deviceId: String): List<TaskIntervalEntity>

    @Query(
        "SELECT * FROM sub_task_intervals WHERE endDateTimeEpochMs IS NULL " +
            "AND (startedByDeviceId IS NULL OR startedByDeviceId = :deviceId)",
    )
    suspend fun getAllOpenSubTaskIntervalsForDevice(deviceId: String): List<SubTaskIntervalEntity>

    // Whether a task-level parked interval is worth keeping at all: a task that owns subtasks is
    // counted through them, so time banked on the task itself renders nowhere. See StrandedTimer.
    @Query("SELECT COUNT(*) FROM project_sub_tasks WHERE parentProjectTaskId = :taskId")
    suspend fun countSubTasks(taskId: String): Int

    @Query(
        "SELECT si.parentSubTaskId FROM sub_task_intervals AS si " +
            "JOIN project_sub_tasks AS s ON s.projectSubTaskId = si.parentSubTaskId " +
            "WHERE s.parentProjectTaskId = :taskId " +
            "ORDER BY si.startDateTimeEpochMs DESC LIMIT 1",
    )
    suspend fun getLastStartedSubTaskId(taskId: String): String?
}
