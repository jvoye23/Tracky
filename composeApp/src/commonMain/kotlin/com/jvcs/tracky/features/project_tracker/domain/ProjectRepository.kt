package com.jvcs.tracky.features.project_tracker.domain

import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectSession
import com.jvcs.tracky.core.domain.model.SessionInterval
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
    suspend fun deleteProjectSession(sessionId: String)
    suspend fun deleteAllProjects()

    suspend fun updateSessionDuration(sessionId: String, newDurationMillis: Long)

    fun getSessionWithIntervalsById(sessionId: String): Flow<ProjectSession?>
    suspend fun upsertSessionInterval(interval: SessionInterval)
    suspend fun getOpenIntervalBySessionId(sessionId: String): SessionInterval?
    
    suspend fun startSession(sessionId: String)
    suspend fun stopSession(sessionId: String)
    suspend fun updateSessionTitle(sessionId: String, title: String)

}