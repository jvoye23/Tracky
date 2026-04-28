package com.jvcs.tracky.features.project_tracker.domain

import com.jvcs.tracky.core.database.relation.ProjectWithSessions
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectSession
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {

    fun getProjects(): Flow<List<Project>>
    suspend fun getProjectById(projectId: String): Project?
    suspend fun getProjectWithSessionsByProjectId(projectId: String): Project?
    suspend fun upsertProject(project: Project): Result<String, DataError>
    suspend fun upsertProjectSession(projectSession: ProjectSession): Result<String, DataError>
    suspend fun updateProject(project: Project): EmptyResult<DataError>
    suspend fun deleteProject(projectId: String)
    suspend fun deleteAllProjects()

    suspend fun updateSessionDuration(sessionId: String, newDurationMillis: Long)

}