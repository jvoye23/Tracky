package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Where projects sit: the archive, the trash and its expiry, the pinned section, and the manual
 * order. Split from [ProjectRepository], which keeps the rows themselves and their sync.
 */
interface ProjectOrganizationRepository {

    fun getArchivedProjects(): Flow<List<Project>>

    fun getTrashedProjects(): Flow<List<Project>>

    suspend fun setProjectArchived(projectId: String, isArchived: Boolean): EmptyResult<DataError>

    suspend fun setProjectTrashed(projectId: String, trashedAt: Instant?): EmptyResult<DataError>

    suspend fun purgeExpiredTrashedProjects(cutoff: Instant): EmptyResult<DataError>

    /**
     * Pins or unpins every id in one gesture. The affected projects move to the top of their new
     * section and the rest of that section is re-indexed behind them.
     */
    suspend fun setProjectsPinned(projectIds: List<String>, isPinned: Boolean): EmptyResult<DataError>

    suspend fun reorderProjects(orderedProjectIds: List<String>): EmptyResult<DataError>
}
