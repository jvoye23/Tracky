package com.jvcs.tracky.features.project.data.project

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.data.mappers.toProject
import com.jvcs.tracky.features.project.data.mappers.toProjectEntity
import com.jvcs.tracky.features.project.data.mappers.toProjectSessionEntity
import com.jvcs.tracky.features.project.data.mappers.toProjectTask
import com.jvcs.tracky.features.project.data.mappers.toTaskInterval
import com.jvcs.tracky.features.project.data.mappers.toTaskIntervalEntity
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.project.LocalProjectDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant
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

    override suspend fun getPinnedProjects(): Result<List<Project>, DataError.Local> = read {
        projectDao.getPinnedProjectsWithTasks().first().map { it.toProject() }
    }

    override suspend fun getExpiredTrashedProjectIds(
        cutoff: Instant
    ): Result<List<String>, DataError.Local> = read {
        projectDao.getExpiredTrashedProjectIds(cutoff.toEpochMilliseconds())
    }

    override suspend fun getProjectById(projectId: String): Result<Project?, DataError.Local> = read {
        projectDao.getProjectById(projectId)?.toProject()
    }

    override suspend fun getProjectWithTasksByProjectId(
        projectId: String
    ): Result<Project?, DataError.Local> = read {
        projectDao.getProjectWithTasksById(projectId)?.toProject()
    }

    override suspend fun getSortIndices(): Result<Map<String, Long?>, DataError.Local> = read {
        projectDao.getSortIndices().associate { it.projectId to it.sortIndex }
    }

    override suspend fun updateSortIndices(
        indices: Map<String, Long>,
        updatedAt: Instant
    ): EmptyResult<DataError.Local> = write {
        projectDao.updateSortIndices(indices, updatedAt.toEpochMilliseconds())
    }

    override suspend fun upsertProject(project: Project): EmptyResult<DataError.Local> = write {
        projectDao.upsertProject(project.toProjectEntity())
    }

    // The pull writes the whole tree, not just the project rows: the server returns every task and
    // interval nested inside GET /api/projects, and dropping them here is what used to make tracked
    // time unrecoverable after a reinstall. A null projectTasks means "not loaded" rather than "no
    // tasks", so it contributes nothing instead of clearing anything.
    override suspend fun upsertProjects(projects: List<Project>): EmptyResult<DataError.Local> = write {
        val tasks = projects.flatMap { it.projectTasks.orEmpty() }
        projectDao.upsertServerTree(
            projects = projects.map { it.toProjectEntity() },
            tasks = tasks.map { it.toProjectSessionEntity() },
            intervals = tasks.flatMap { task -> task.intervals }
                .map { it.toTaskIntervalEntity() },
        )
    }

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError.Local> = write {
        projectDao.deleteProject(projectId)
    }

    override suspend fun deleteAllProjects(): EmptyResult<DataError.Local> = write {
        // task_intervals cascades from both project_records and projects, so dropping the projects
        // takes every task and interval with it.
        projectDao.deleteAllProjects()
    }

    // --- Tasks and intervals ---------------------------------------------------------------------
    // Still here because the split into their own data sources is the next change; only the error
    // handling has moved so far.

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError.Local> = write {
        projectDao.upsertProjectRecord(projectTask.toProjectSessionEntity())
    }

    override suspend fun deleteProjectTask(taskId: String): EmptyResult<DataError.Local> = write {
        projectDao.deleteProjectRecord(taskId)
    }

    override suspend fun updateTaskDuration(
        taskId: String,
        newDurationMillis: Long
    ): EmptyResult<DataError.Local> = write {
        projectDao.updateTaskDuration(taskId, newDurationMillis)
    }

    override suspend fun updateTaskTitle(taskId: String, title: String): EmptyResult<DataError.Local> = write {
        projectDao.updateTaskTitle(taskId, title)
    }

    override fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> {
        return projectDao.getTaskWithIntervalsById(taskId)
            .map { it?.toProjectTask() }
    }

    override suspend fun getTaskById(taskId: String): Result<ProjectTask?, DataError.Local> = read {
        projectDao.getTaskWithIntervalsById(taskId).first()?.toProjectTask()
    }

    override suspend fun startTask(taskId: String): Result<TaskInterval, DataError.Local> {
        return try {
            val interval = withContext(dbWriteDispatcher) {
                // The owning project has to be read before the interval can be written: it is part
                // of the row now, and the cascading foreign key would reject an interval whose task
                // no longer exists anyway.
                val task = projectDao.getTaskById(taskId) ?: return@withContext null
                val now = timeProvider.nowInstant
                val interval = TaskIntervalEntity(
                    intervalId = Uuid.random().toString(),
                    parentTaskId = taskId,
                    parentProjectId = task.parentProjectId,
                    startDateTimeEpochMs = now.toEpochMilliseconds(),
                    endDateTimeEpochMs = null,
                    durationMillis = 0L
                )
                projectDao.upsertTaskInterval(interval)
                projectDao.updateSessionTimerStatus(taskId, true)
                interval
            } ?: return Result.Error(DataError.Local.NOT_FOUND)
            Result.Success(interval.toTaskInterval())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    override suspend fun stopTask(taskId: String): Result<TaskInterval?, DataError.Local> {
        return try {
            val closedInterval = withContext(dbWriteDispatcher) {
                val openInterval = projectDao.getOpenIntervalBySessionId(taskId)
                val updatedInterval = if (openInterval != null) {
                    val now = timeProvider.nowInstant
                    val startInstant =
                        Instant.fromEpochMilliseconds(openInterval.startDateTimeEpochMs)
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
            Result.Success(closedInterval?.toTaskInterval())
        } catch (e: Exception) {
            if(e is CancellationException) throw e
            Result.Error(DataError.Local.DISK_FULL)
        }
    }

    /** Reads run on the caller's context; Room already moves the query off the main thread. */
    private inline fun <T> read(block: () -> T): Result<T, DataError.Local> {
        return try {
            Result.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    /**
     * Writes are funnelled through a single-threaded dispatcher. The reactive sync does bulk writes
     * on the application scope while the timer writes intervals; letting those interleave across
     * connections is what corrupted the WAL file (SQLITE_NOTADB).
     */
    private suspend fun write(block: suspend () -> Unit): EmptyResult<DataError.Local> {
        return try {
            withContext(dbWriteDispatcher) { block() }
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(DataError.Local.DISK_FULL)
        }
    }
}
