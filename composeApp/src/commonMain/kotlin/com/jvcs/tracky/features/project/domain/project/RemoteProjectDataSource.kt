package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project
import kotlin.time.Instant

interface RemoteProjectDataSource {
    /**
     * The full pull. Tasks and intervals come back nested inside each project, which is why there
     * is no separate remote read for them anywhere in the feature.
     */
    suspend fun getProjects(): Result<List<Project>, DataError.Remote>
    suspend fun postProject(project: Project): Result<Project, DataError.Remote>
    suspend fun updateProject(project: Project): Result<Project, DataError.Remote>
    suspend fun deleteProject(projectId: String): EmptyResult<DataError.Remote>
    /** Pushes a whole reorder as one call: only the moved projects, one shared updatedAt. */
    suspend fun reorderProjects(indices: Map<String, Long>, updatedAt: Instant): EmptyResult<DataError.Remote>
}
