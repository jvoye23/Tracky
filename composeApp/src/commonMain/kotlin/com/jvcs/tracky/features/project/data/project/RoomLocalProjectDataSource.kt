package com.jvcs.tracky.features.project.data.project

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.domain.sync.SyncChanges
import com.jvcs.tracky.core.domain.sync.Tombstone
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.platformIoDispatcher
import com.jvcs.tracky.features.project.data.mappers.toProject
import com.jvcs.tracky.features.project.data.mappers.toProjectEntity
import com.jvcs.tracky.features.project.data.mappers.toProjectTaskEntity
import com.jvcs.tracky.features.project.data.mappers.toProjectSubTaskEntity
import com.jvcs.tracky.features.project.data.mappers.toSubTaskIntervalEntity
import com.jvcs.tracky.features.project.data.mappers.toTaskIntervalEntity
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.project.LocalProjectDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant

class RoomLocalProjectDataSource (
    private val projectDao: ProjectDao
): LocalProjectDataSource {

    private val dbWriteDispatcher = platformIoDispatcher.limitedParallelism(1)

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

    override fun observeProjectById(projectId: String): Flow<Project?> =
        projectDao.observeProjectById(projectId).map { it?.toProject() }

    override suspend fun getProjectWithTasksByProjectId(
        projectId: String
    ): Result<Project?, DataError.Local> = read {
        projectDao.getProjectWithTaskTreeById(projectId)?.toProject()
    }

    override fun observeProjectWithTaskTreeById(projectId: String): Flow<Project?> =
        projectDao.observeProjectWithTaskTreeById(projectId).map { it?.toProject() }

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

    // The pull writes the whole tree, not just the project rows: the server returns all four levels
    // nested inside GET /api/projects, and dropping them here is what used to make tracked time
    // unrecoverable after a reinstall. A null projectTasks or subTasks means "not loaded" rather
    // than "none", so it contributes nothing instead of clearing anything.
    override suspend fun upsertProjects(projects: List<Project>): EmptyResult<DataError.Local> = write {
        val tasks = projects.flatMap { it.projectTasks.orEmpty() }
        val subTasks = tasks.flatMap { task -> task.subTasks.orEmpty() }
        projectDao.upsertServerTree(
            projects = projects.map { it.toProjectEntity() },
            tasks = tasks.map { it.toProjectTaskEntity() },
            intervals = tasks.flatMap { task -> task.intervals }
                .map { it.toTaskIntervalEntity() },
            subTasks = subTasks.map { it.toProjectSubTaskEntity() },
            subTaskIntervals = subTasks.flatMap { it.subTaskIntervals }
                .map { it.toSubTaskIntervalEntity() },
        )
    }

    override suspend fun applyDelta(changes: SyncChanges): EmptyResult<DataError.Local> = write {
        // The feed is flat, unlike GET /api/projects, so there is nothing to walk down — but the
        // tombstones have to be split by level, because each one names a different table.
        val deletions = changes.tombstones.groupBy({ it.entityType }, { it.entityId })
        projectDao.applyDelta(
            projects = changes.projects.map { it.toProjectEntity() },
            tasks = changes.tasks.map { it.toProjectTaskEntity() },
            intervals = changes.taskIntervals.map { it.toTaskIntervalEntity() },
            subTasks = changes.subTasks.map { it.toProjectSubTaskEntity() },
            subTaskIntervals = changes.subTaskIntervals.map { it.toSubTaskIntervalEntity() },
            deletedProjectIds = deletions[Tombstone.PROJECT].orEmpty(),
            deletedTaskIds = deletions[Tombstone.TASK].orEmpty(),
            deletedIntervalIds = deletions[Tombstone.TASK_INTERVAL].orEmpty(),
            deletedSubTaskIds = deletions[Tombstone.SUB_TASK].orEmpty(),
            deletedSubTaskIntervalIds = deletions[Tombstone.SUB_TASK_INTERVAL].orEmpty()
        )
        // These are the *wire's* names, not the outbox's — see Tombstone's companion, which is
        // where they used to come from and where two of the five were wrong.
        //
        // An entityType this build does not recognise is simply absent from the map above, so a
        // newer server knowing about a kind of row this one does not is not a reason to fail. That
        // tolerance is only safe while the five names above are known to be right: it cannot tell a
        // future level from a misspelt current one, which is exactly how task and subtask deletions
        // went missing without a single error. RoomLocalProjectDataSourceTombstoneTest pins each
        // name against the spec for that reason.
    }

    override suspend fun applyTimerEcho(
        taskIntervals: List<TaskInterval>,
        subTaskIntervals: List<SubTaskInterval>
    ): EmptyResult<DataError.Local> = write {
        // upsertServerTree, not applyDelta: an echo names rows the server changed, never rows it
        // removed, and that method is the one that promises never to delete.
        projectDao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = taskIntervals.map { it.toTaskIntervalEntity() },
            subTaskIntervals = subTaskIntervals.map { it.toSubTaskIntervalEntity() }
        )
    }

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError.Local> = write {
        projectDao.deleteProject(projectId)
    }

    override suspend fun deleteAllProjects(): EmptyResult<DataError.Local> = write {
        // task_intervals cascades from both project_tasks and projects, so dropping the projects
        // takes every task and interval with it.
        projectDao.deleteAllProjects()
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
