package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

typealias ProjectId = String

interface LocalProjectDataSource {
    fun getProjects(): Flow<List<Project>>
    fun getActiveProjects(): Flow<List<Project>>
    fun getArchivedProjects(): Flow<List<Project>>
    fun getTrashedProjects(): Flow<List<Project>>
    fun getPinnedProjects(): Flow<List<Project>>
    suspend fun getExpiredTrashedProjectIds(cutoff: Instant): List<String>
    suspend fun getProjectById(projectId: String): Project?
    suspend fun getProjectWithTasksByProjectId(projectId: String): Project?
    /** Current sortIndex per project id. A null value means the project was never manually ordered. */
    suspend fun getSortIndices(): Map<String, Long?>
    /** Writes every index in one transaction, so a reorder can never land half-applied. */
    suspend fun updateSortIndices(indices: Map<String, Long>, updatedAt: Instant): EmptyResult<DataError>
    suspend fun upsertProject(project: Project): EmptyResult<DataError>
    suspend fun upsertProjects(projects: List<Project>): EmptyResult<DataError>
    suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError>
    suspend fun deleteProject(projectId: String)
    suspend fun deleteProjectTask(taskId: String)
    suspend fun deleteAllProjects()
    suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long)
    fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?>
    suspend fun upsertTaskInterval(interval: TaskInterval): EmptyResult<DataError>
    suspend fun getOpenIntervalByTaskId(taskId: String): TaskInterval?

    /**
     * Reads a single interval by id.
     *
     * The pending-sync queue stores only ids, so a queued interval op has to re-read the row from
     * local state when it finally drains — and a missing row is how the drain knows to drop the op.
     */
    suspend fun getIntervalById(intervalId: String): TaskInterval?
    suspend fun deleteTaskInterval(intervalId: String): EmptyResult<DataError>

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
    suspend fun updateTaskTitle(taskId: String, title: String)
}

