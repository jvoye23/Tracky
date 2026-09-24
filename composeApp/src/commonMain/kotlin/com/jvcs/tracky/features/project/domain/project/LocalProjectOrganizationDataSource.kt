package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Where projects sit: the archived, trashed and pinned sections, trash expiry, and the manual
 * order. Split from [LocalProjectDataSource], which keeps the rows themselves.
 */
interface LocalProjectOrganizationDataSource {

    fun getArchivedProjects(): Flow<List<Project>>

    fun getTrashedProjects(): Flow<List<Project>>

    /** One-shot: the pin reindex needs the current pinned section, not a stream of it. */
    suspend fun getPinnedProjects(): Result<List<Project>, DataError.Local>

    suspend fun getExpiredTrashedProjectIds(cutoff: Instant): Result<List<String>, DataError.Local>

    /** Current sortIndex per project id. A null value means the project was never manually ordered. */
    suspend fun getSortIndices(): Result<Map<String, Long?>, DataError.Local>

    /** Writes every index in one transaction, so a reorder can never land half-applied. */
    suspend fun updateSortIndices(indices: Map<String, Long>, updatedAt: Instant): EmptyResult<DataError.Local>
}
