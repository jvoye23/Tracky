package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval
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

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? {
        return projectDao.getProjectWithTasksById(projectId)?.toProject()
    }

    override suspend fun upsertProject(project: Project): Result<String, DataError> {
        return try {
            val result = projectDao.upsertProject(project.toProjectEntity())
            Result.Success(result.toString())

        } catch (e: Exception) {
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun upsertProjectTask(projectTask: ProjectTask): Result<String, DataError> {
        return try {
            val result = projectDao.upsertProjectRecord(projectTask.toProjectSessionEntity())
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

    override suspend fun deleteProjectTask(taskId: String) {
        projectDao.deleteProjectRecord(taskId)
    }

    override suspend fun deleteAllProjects() {
        projectDao.deleteAllProjects()

    }

    override suspend fun updateTaskDuration(
        taskId: String,
        newDurationMillis: Long
    ) {
        projectDao.updateTaskDuration(taskId, newDurationMillis)
    }

    override fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> {
        return projectDao.getTaskWithIntervalsById(taskId)
            .map { it?.toProjectSession() }
    }

    override suspend fun upsertTaskInterval(interval: TaskInterval) {
        projectDao.upsertTaskInterval(interval.toSessionIntervalEntity())
    }

    override suspend fun getOpenIntervalByTaskId(taskId: String): TaskInterval? {
        return projectDao.getOpenIntervalBySessionId(taskId)?.toSessionInterval()
    }

    override suspend fun startTask(taskId: String) {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val interval = TaskIntervalEntity(
            parentTaskId = taskId,
            startDateTimeEpochMs = now,
            endDateTimeEpochMs = null,
            durationMillis = 0L
        )
        projectDao.upsertTaskInterval(interval)
        projectDao.updateSessionTimerStatus(taskId, true)
    }

    override suspend fun stopTask(taskId: String) {
        val openInterval = projectDao.getOpenIntervalBySessionId(taskId)
        if (openInterval != null) {
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val duration = now - openInterval.startDateTimeEpochMs
            val updatedInterval = openInterval.copy(
                endDateTimeEpochMs = now,
                durationMillis = duration
            )
            projectDao.upsertTaskInterval(updatedInterval)
            
            projectDao.addTaskDuration(taskId, duration)
        }
        projectDao.updateSessionTimerStatus(taskId, false)
    }

    override suspend fun updateTaskTitle(taskId: String, title: String) {
        projectDao.updateTaskTitle(taskId, title)
    }

}