package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

typealias ProjectId = String

/**
 * Room-facing contract.
 *
 * Every suspend read and write hands back a [Result]: the database is where `SQLiteException` and
 * friends originate, so this is the layer that turns them into a typed [DataError.Local]. Callers
 * above never see a raw exception. The `Flow` reads stay unwrapped — they are long-lived UI streams,
 * not one-shot calls.
 */
interface LocalProjectDataSource {
    fun getProjects(): Flow<List<Project>>
    fun getActiveProjects(): Flow<List<Project>>
    fun getArchivedProjects(): Flow<List<Project>>
    fun getTrashedProjects(): Flow<List<Project>>
    /** One-shot: the pin reindex needs the current pinned section, not a stream of it. */
    suspend fun getPinnedProjects(): Result<List<Project>, DataError.Local>
    suspend fun getExpiredTrashedProjectIds(cutoff: Instant): Result<List<String>, DataError.Local>
    suspend fun getProjectById(projectId: String): Result<Project?, DataError.Local>
    suspend fun getProjectWithTasksByProjectId(projectId: String): Result<Project?, DataError.Local>
    /** Current sortIndex per project id. A null value means the project was never manually ordered. */
    suspend fun getSortIndices(): Result<Map<String, Long?>, DataError.Local>
    /** Writes every index in one transaction, so a reorder can never land half-applied. */
    suspend fun updateSortIndices(indices: Map<String, Long>, updatedAt: Instant): EmptyResult<DataError.Local>
    suspend fun upsertProject(project: Project): EmptyResult<DataError.Local>
    suspend fun upsertProjects(projects: List<Project>): EmptyResult<DataError.Local>
    suspend fun deleteProject(projectId: String): EmptyResult<DataError.Local>
    suspend fun deleteAllProjects(): EmptyResult<DataError.Local>

    suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError.Local>
    suspend fun deleteProjectTask(taskId: String): EmptyResult<DataError.Local>
    suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long): EmptyResult<DataError.Local>
    suspend fun updateTaskTitle(taskId: String, title: String): EmptyResult<DataError.Local>
    fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?>
    /** One-shot read of the same row — repositories need a value, not a subscription. */
    suspend fun getTaskById(taskId: String): Result<ProjectTask?, DataError.Local>

    /**
     * Opens a new interval and flags the task's timer as running.
     *
     * Returns the interval it created so the caller can push it remotely — the id is generated in
     * here, so there is no other way for the repository to know which row to sync.
     */
    suspend fun startTask(taskId: String): Result<TaskInterval, DataError.Local>

    /**
     * Closes the task's open interval, adds its duration to the task and clears the timer flag.
     *
     * Returns the interval it just closed, or null when the timer was not running — again so the
     * caller can push exactly that row.
     */
    suspend fun stopTask(taskId: String): Result<TaskInterval?, DataError.Local>

    suspend fun upsertTaskInterval(interval: TaskInterval): EmptyResult<DataError.Local>
    suspend fun getOpenIntervalByTaskId(taskId: String): Result<TaskInterval?, DataError.Local>

    /**
     * Reads a single interval by id.
     *
     * The pending-sync queue stores only ids, so a queued interval op has to re-read the row from
     * local state when it finally drains — and a missing row is how the drain knows to drop the op.
     */
    suspend fun getIntervalById(intervalId: String): Result<TaskInterval?, DataError.Local>
    suspend fun deleteTaskInterval(intervalId: String): EmptyResult<DataError.Local>
}
