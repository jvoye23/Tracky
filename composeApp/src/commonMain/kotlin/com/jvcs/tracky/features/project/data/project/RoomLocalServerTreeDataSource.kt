package com.jvcs.tracky.features.project.data.project

import com.jvcs.tracky.core.database.ServerTombstones
import com.jvcs.tracky.core.database.ServerTreeRows
import com.jvcs.tracky.core.database.ServerTreeWriter
import com.jvcs.tracky.core.domain.sync.SyncChanges
import com.jvcs.tracky.core.domain.sync.Tombstone
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.data.mappers.toProjectEntity
import com.jvcs.tracky.features.project.data.mappers.toProjectSubTaskEntity
import com.jvcs.tracky.features.project.data.mappers.toProjectTaskEntity
import com.jvcs.tracky.features.project.data.mappers.toSubTaskIntervalEntity
import com.jvcs.tracky.features.project.data.mappers.toTaskIntervalEntity
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.project.LocalServerTreeDataSource

class RoomLocalServerTreeDataSource(private val serverTreeWriter: ServerTreeWriter) : LocalServerTreeDataSource {

    // The pull writes the whole tree, not just the project rows: the server returns all four levels
    // nested inside GET /api/projects, and dropping them here is what used to make tracked time
    // unrecoverable after a reinstall. A null projectTasks or subTasks means "not loaded" rather
    // than "none", so it contributes nothing instead of clearing anything.
    override suspend fun upsertProjects(projects: List<Project>): EmptyResult<DataError.Local> =
        write {
            val tasks = projects.flatMap { it.projectTasks.orEmpty() }
            val subTasks = tasks.flatMap { task -> task.subTasks.orEmpty() }
            serverTreeWriter.upsertServerTree(
                projects = projects.map { it.toProjectEntity() },
                tasks = tasks.map { it.toProjectTaskEntity() },
                intervals =
                    tasks
                        .flatMap { task -> task.intervals }
                        .map { it.toTaskIntervalEntity() },
                subTasks = subTasks.map { it.toProjectSubTaskEntity() },
                subTaskIntervals =
                    subTasks
                        .flatMap { it.subTaskIntervals }
                        .map { it.toSubTaskIntervalEntity() },
            )
        }

    override suspend fun applyDelta(changes: SyncChanges): EmptyResult<DataError.Local> =
        write {
            // The feed is flat, unlike GET /api/projects, so there is nothing to walk down — but the
            // tombstones have to be split by level, because each one names a different table.
            val deletions = changes.tombstones.groupBy({ it.entityType }, { it.entityId })
            serverTreeWriter.applyDelta(
                upserts =
                    ServerTreeRows(
                        projects = changes.projects.map { it.toProjectEntity() },
                        tasks = changes.tasks.map { it.toProjectTaskEntity() },
                        intervals = changes.taskIntervals.map { it.toTaskIntervalEntity() },
                        subTasks = changes.subTasks.map { it.toProjectSubTaskEntity() },
                        subTaskIntervals = changes.subTaskIntervals.map { it.toSubTaskIntervalEntity() },
                    ),
                deletions =
                    ServerTombstones(
                        projectIds = deletions[Tombstone.PROJECT].orEmpty(),
                        taskIds = deletions[Tombstone.TASK].orEmpty(),
                        intervalIds = deletions[Tombstone.TASK_INTERVAL].orEmpty(),
                        subTaskIds = deletions[Tombstone.SUB_TASK].orEmpty(),
                        subTaskIntervalIds = deletions[Tombstone.SUB_TASK_INTERVAL].orEmpty(),
                    ),
            )
            // These are the *wire's* names, not the outbox's — see Tombstone's companion, which is
            // where they used to come from and where two of the five were wrong.
            //
            // An entityType this build does not recognise is simply absent from the map above, so a
            // newer server knowing about a kind of row this one does not is not a reason to fail. That
            // tolerance is only safe while the five names above are known to be right: it cannot tell a
            // future level from a misspelt current one, which is exactly how task and subtask deletions
            // went missing without a single error. RoomLocalServerTreeDataSourceTombstoneTest pins each
            // name against the spec for that reason.
        }

    override suspend fun applyTimerEcho(
        taskIntervals: List<TaskInterval>,
        subTaskIntervals: List<SubTaskInterval>,
    ): EmptyResult<DataError.Local> =
        write {
            // upsertServerTree, not applyDelta: an echo names rows the server changed, never rows it
            // removed, and that method is the one that promises never to delete.
            serverTreeWriter.upsertServerTree(
                projects = emptyList(),
                tasks = emptyList(),
                intervals = taskIntervals.map { it.toTaskIntervalEntity() },
                subTaskIntervals = subTaskIntervals.map { it.toSubTaskIntervalEntity() },
            )
        }

    private suspend fun write(block: suspend () -> Unit): EmptyResult<DataError.Local> = roomWrite(TAG, block)

    private companion object {
        const val TAG = "RoomLocalServerTreeDataSource"
    }
}
