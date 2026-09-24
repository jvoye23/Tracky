package com.jvcs.tracky.features.project.data.project

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.database.ServerTombstones
import com.jvcs.tracky.core.database.ServerTreeRows
import com.jvcs.tracky.core.database.ServerTreeWriter
import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.dao.ProjectTreeDao
import com.jvcs.tracky.core.database.dao.SortOrderDao
import com.jvcs.tracky.core.domain.sync.SyncChanges
import com.jvcs.tracky.core.domain.sync.Tombstone
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.platformIoDispatcher
import com.jvcs.tracky.features.project.data.mappers.toProject
import com.jvcs.tracky.features.project.data.mappers.toProjectEntity
import com.jvcs.tracky.features.project.data.mappers.toProjectSubTaskEntity
import com.jvcs.tracky.features.project.data.mappers.toProjectTaskEntity
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

class RoomLocalProjectDataSource(private val projectDao: ProjectDao, private val projectTreeDao: ProjectTreeDao) :
    LocalProjectDataSource {

    override fun getProjects(): Flow<List<Project>> =
        projectTreeDao
            .getProjectsWithTasks()
            .map { list -> list.map { it.toProject() } }

    override fun getActiveProjects(): Flow<List<Project>> =
        projectTreeDao
            .getActiveProjectsWithTasks()
            .map { list -> list.map { it.toProject() } }

    override suspend fun getProjectById(projectId: String): Result<Project?, DataError.Local> =
        read {
            projectDao.getProjectById(projectId)?.toProject()
        }

    override fun observeProjectById(projectId: String): Flow<Project?> =
        projectDao.observeProjectById(projectId).map { it?.toProject() }

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Result<Project?, DataError.Local> =
        read {
            projectTreeDao.getProjectWithTaskTreeById(projectId)?.toProject()
        }

    override fun observeProjectWithTaskTreeById(projectId: String): Flow<Project?> =
        projectTreeDao.observeProjectWithTaskTreeById(projectId).map { it?.toProject() }

    override suspend fun upsertProject(project: Project): EmptyResult<DataError.Local> =
        write {
            projectDao.upsertProject(project.toProjectEntity())
        }

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError.Local> =
        write {
            projectDao.deleteProject(projectId)
        }

    override suspend fun deleteAllProjects(): EmptyResult<DataError.Local> =
        write {
            // task_intervals cascades from both project_tasks and projects, so dropping the projects
            // takes every task and interval with it.
            projectDao.deleteAllProjects()
        }

    private inline fun <T> read(block: () -> T): Result<T, DataError.Local> = roomRead(TAG, block)

    private suspend fun write(block: suspend () -> Unit): EmptyResult<DataError.Local> = roomWrite(TAG, block)

    private companion object {
        const val TAG = "RoomLocalProjectDataSource"
    }
}
