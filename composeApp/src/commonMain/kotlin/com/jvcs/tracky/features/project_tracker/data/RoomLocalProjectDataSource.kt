package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.SessionIntervalEntity
import com.jvcs.tracky.core.database.relation.ProjectWithSessions
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectSession
import com.jvcs.tracky.core.domain.model.SessionInterval
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.mapper.toProject
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.coroutines.flow.Flow
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.mapper.toProjectEntity
import com.jvcs.tracky.core.mapper.toProjectSession
import com.jvcs.tracky.core.mapper.toProjectSessionEntity
import com.jvcs.tracky.core.mapper.toSessionInterval
import com.jvcs.tracky.core.mapper.toSessionIntervalEntity
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class RoomLocalProjectDataSource (
    private val projectDao: ProjectDao
): ProjectRepository {
    override fun getProjects(): Flow<List<Project>> {
        val projects = projectDao.getProjects()
            .map { projectEntity -> projectEntity.map { it.toProject() } }

        return projects
    }

    override suspend fun getProjectById(projectId: String): Project? {
        return projectDao.getProjectById(projectId)?.toProject()

    }

    override suspend fun getProjectWithSessionsByProjectId(projectId: String): Project? {
        return projectDao.getProjectWithSessionsById(projectId)?.toProject()
    }

    override suspend fun upsertProject(project: Project): Result<String, DataError> {
        return try {
            val result = projectDao.upsertProject(project.toProjectEntity())
            Result.Success(result.toString())

        } catch (e: Exception) {
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun upsertProjectSession(projectSession: ProjectSession): Result<String, DataError> {
        return try {
            val result = projectDao.upsertProjectRecord(projectSession.toProjectSessionEntity())
            Result.Success(result.toString())
        } catch (e: Exception) {
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun updateProject(project: Project): EmptyResult<DataError> {
        return try {
            val result = projectDao.upsertProject(project.toProjectEntity())
            Result.Success(result)

        } catch (e: Exception) {
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun deleteProject(projectId: String) {
        projectDao.deleteProject(projectId)
    }

    override suspend fun deleteProjectSession(sessionId: String) {
        projectDao.deleteProjectRecord(sessionId)
    }

    override suspend fun deleteAllProjects() {
        projectDao.deleteAllProjects()

    }

    override suspend fun updateSessionDuration(
        sessionId: String,
        newDurationMillis: Long
    ) {
        projectDao.updateSessionDuration(sessionId, newDurationMillis)
    }

    override fun getSessionWithIntervalsById(sessionId: String): Flow<ProjectSession?> {
        return projectDao.getSessionWithIntervalsById(sessionId)
            .map { it?.toProjectSession() }
    }

    override suspend fun upsertSessionInterval(interval: SessionInterval) {
        projectDao.upsertSessionInterval(interval.toSessionIntervalEntity())
    }

    override suspend fun getOpenIntervalBySessionId(sessionId: String): SessionInterval? {
        return projectDao.getOpenIntervalBySessionId(sessionId)?.toSessionInterval()
    }

    override suspend fun startSession(sessionId: String) {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val interval = SessionIntervalEntity(
            parentSessionId = sessionId,
            startDateTimeEpochMs = now,
            endDateTimeEpochMs = null,
            durationMillis = 0L
        )
        projectDao.upsertSessionInterval(interval)
        projectDao.updateSessionTimerStatus(sessionId, true)
    }

    override suspend fun stopSession(sessionId: String) {
        val openInterval = projectDao.getOpenIntervalBySessionId(sessionId)
        if (openInterval != null) {
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val duration = now - openInterval.startDateTimeEpochMs
            val updatedInterval = openInterval.copy(
                endDateTimeEpochMs = now,
                durationMillis = duration
            )
            projectDao.upsertSessionInterval(updatedInterval)
            
            projectDao.addSessionDuration(sessionId, duration)
        }
        projectDao.updateSessionTimerStatus(sessionId, false)
    }

    override suspend fun updateSessionTitle(sessionId: String, title: String) {
        projectDao.updateSessionTitle(sessionId, title)
    }

}