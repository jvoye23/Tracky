@file:OptIn(ExperimentalUuidApi::class)

package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.mapper.toProject
import com.jvcs.tracky.core.mapper.toProjectEntity
import com.jvcs.tracky.core.mapper.toProjectSession
import com.jvcs.tracky.core.mapper.toProjectSessionEntity
import com.jvcs.tracky.core.mapper.toSessionInterval
import com.jvcs.tracky.core.mapper.toSessionIntervalEntity
import com.jvcs.tracky.features.project_tracker.domain.LocalProjectDataSource
import com.jvcs.tracky.features.project_tracker.domain.ProjectId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class RoomLocalProjectDataSource (
    private val projectDao: ProjectDao,
    private val timeProvider: TimeProvider
): LocalProjectDataSource {

    private val dbWriteDispatcher = Dispatchers.IO.limitedParallelism(1)

    override fun getProjects(): Flow<List<Project>> {
        return projectDao.getProjectsWithTasks()
            .map { list -> list.map { it.toProject() } }
    }

    override fun getActiveProjects(): Flow<List<Project>> {
        return projectDao.getActiveProjectsWithTasks()
            .map { list -> list.map { it.toProject()} }
    }

    override fun getArchivedProjects(): Flow<List<Project>> {
        return projectDao.getArchivedProjectsWithTasks()
            .map { list -> list.map { it.toProject() } }
    }

    override fun getTrashedProjects(): Flow<List<Project>> {
        return projectDao.getTrashedProjectsWithTasks()
            .map { list -> list.map { it.toProject() } }
    }

    override fun getPinnedProjects(): Flow<List<Project>> {
        return projectDao.getPinnedProjectsWithTasks()
            .map { list -> list.map { it.toProject() } }
    }

    override suspend fun getExpiredTrashedProjectIds(cutoff: Instant): List<String> {
        return projectDao.getExpiredTrashedProjectIds(cutoff.toEpochMilliseconds())
    }

    override suspend fun getProjectById(projectId: String): Project? {
        return projectDao.getProjectById(projectId)?.toProject()

    }

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? {
        return projectDao.getProjectWithTasksById(projectId)?.toProject()
    }

    override suspend fun getSortIndices(): Map<String, Long?> {
        return projectDao.getSortIndices().associate { it.projectId to it.sortIndex }
    }

    override suspend fun updateSortIndices(
        indices: Map<String, Long>,
        updatedAt: Instant
    ): EmptyResult<DataError> {
        return try {
            withContext(dbWriteDispatcher) {
                projectDao.updateSortIndices(indices, updatedAt.toEpochMilliseconds())
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            if(e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun upsertProject(project: Project): EmptyResult<DataError> {
        return try {
            withContext(dbWriteDispatcher) {
                projectDao.upsertProject(project.toProjectEntity())
            }
            Result.Success(Unit)

        } catch (e: Exception) {
            if(e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun upsertProjects(projects: List<Project>): EmptyResult<DataError> {
        return try {
            withContext(dbWriteDispatcher) {
                projectDao.upsertProjects(projects.map { it.toProjectEntity() })
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            if(e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> {
        return try {
            withContext(dbWriteDispatcher) {
                projectDao.upsertProjectRecord(projectTask.toProjectSessionEntity())
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            if(e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun deleteProject(projectId: String) = withContext(dbWriteDispatcher) {
        projectDao.deleteProject(projectId)
    }

    override suspend fun deleteProjectTask(taskId: String) = withContext(dbWriteDispatcher) {
        projectDao.deleteProjectRecord(taskId)
    }

    override suspend fun deleteAllProjects() = withContext(dbWriteDispatcher) {
        // task_intervals has no foreign key onto project_records, so dropping the projects would
        // otherwise strand every interval row.
        projectDao.deleteAllTaskIntervals()
        projectDao.deleteAllProjects()
    }

    override suspend fun updateTaskDuration(
        taskId: String,
        newDurationMillis: Long
    ) = withContext(dbWriteDispatcher) {
        projectDao.updateTaskDuration(taskId, newDurationMillis)
    }

    override fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> {
        return projectDao.getTaskWithIntervalsById(taskId)
            .map { it?.toProjectSession() }
    }

    override suspend fun upsertTaskInterval(interval: TaskInterval): EmptyResult<DataError> {
        return try {
            withContext(dbWriteDispatcher) {
                projectDao.upsertTaskInterval(interval.toSessionIntervalEntity())
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            if(e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun getOpenIntervalByTaskId(taskId: String): TaskInterval? {
        return projectDao.getOpenIntervalBySessionId(taskId)?.toSessionInterval()
    }

    override suspend fun deleteTaskInterval(intervalId: String): EmptyResult<DataError> {
        return try {
            withContext(dbWriteDispatcher) {
                projectDao.deleteTaskInterval(intervalId)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun startTask(taskId: String): Result<TaskInterval, DataError.Local> {
        return try {
            val interval = withContext(dbWriteDispatcher) {
                val now = timeProvider.nowInstant
                val interval = TaskIntervalEntity(
                    intervalId = Uuid.random().toString(),
                    parentTaskId = taskId,
                    startDateTimeEpochMs = now.toEpochMilliseconds(),
                    endDateTimeEpochMs = null,
                    durationMillis = 0L
                )
                projectDao.upsertTaskInterval(interval)
                projectDao.updateSessionTimerStatus(taskId, true)
                interval
            }
            Result.Success(interval.toSessionInterval())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun stopTask(taskId: String): Result<TaskInterval?, DataError.Local>  {
        return try {
            val closedInterval = withContext(dbWriteDispatcher) {
                val openInterval = projectDao.getOpenIntervalBySessionId(taskId)
                val updatedInterval = if (openInterval != null) {
                    val now = timeProvider.nowInstant
                    val startInstant = Instant.fromEpochMilliseconds(openInterval.startDateTimeEpochMs)
                    val duration = (now - startInstant).inWholeMilliseconds
                    val updatedInterval = openInterval.copy(
                        endDateTimeEpochMs = now.toEpochMilliseconds(),
                        durationMillis = duration
                    )
                    projectDao.upsertTaskInterval(updatedInterval)

                    projectDao.addTaskDuration(taskId, duration)
                    updatedInterval
                } else null
                projectDao.updateSessionTimerStatus(taskId, false)
                updatedInterval
            }
            Result.Success(closedInterval?.toSessionInterval())
        } catch (e: Exception) {
            if(e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun updateTaskTitle(taskId: String, title: String) = withContext(dbWriteDispatcher) {
        projectDao.updateTaskTitle(taskId, title)
    }
}