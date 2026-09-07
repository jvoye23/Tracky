package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

typealias ProjectId = String

/**
 * Room-facing project contract.
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
    /** The project row as a live stream, without its task tree. Emits null once the row is gone. */
    fun observeProjectById(projectId: String): Flow<Project?>
    suspend fun getProjectWithTasksByProjectId(projectId: String): Result<Project?, DataError.Local>
    /** Current sortIndex per project id. A null value means the project was never manually ordered. */
    suspend fun getSortIndices(): Result<Map<String, Long?>, DataError.Local>
    /** Writes every index in one transaction, so a reorder can never land half-applied. */
    suspend fun updateSortIndices(indices: Map<String, Long>, updatedAt: Instant): EmptyResult<DataError.Local>
    suspend fun upsertProject(project: Project): EmptyResult<DataError.Local>
    suspend fun upsertProjects(projects: List<Project>): EmptyResult<DataError.Local>
    suspend fun deleteProject(projectId: String): EmptyResult<DataError.Local>
    suspend fun deleteAllProjects(): EmptyResult<DataError.Local>
}
