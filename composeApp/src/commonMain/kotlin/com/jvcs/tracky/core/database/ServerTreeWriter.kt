package com.jvcs.tracky.core.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.sync.serverWinsOnPull
import com.jvcs.tracky.core.domain.sync.serverWinsOnPullForInterval

/** The rows of a server tree, one list per level. An empty level contributes nothing. */
data class ServerTreeRows(
    val projects: List<ProjectEntity> = emptyList(),
    val tasks: List<ProjectTaskEntity> = emptyList(),
    val intervals: List<TaskIntervalEntity> = emptyList(),
    val subTasks: List<ProjectSubTaskEntity> = emptyList(),
    val subTaskIntervals: List<SubTaskIntervalEntity> = emptyList(),
)

/** The ids of the rows the server says are gone, one list per level. */
data class ServerTombstones(
    val projectIds: List<String> = emptyList(),
    val taskIds: List<String> = emptyList(),
    val intervalIds: List<String> = emptyList(),
    val subTaskIds: List<String> = emptyList(),
    val subTaskIntervalIds: List<String> = emptyList(),
)

/**
 * Writes what the server sent into every level of the project tree, in one transaction.
 *
 * This used to be two `@Transaction` methods on a single DAO that spanned all five tables. It is a
 * class of its own now that each table has its own DAO: a writer transaction on [database] is the
 * one thing that can hold several DAOs' writes together.
 */
class ServerTreeWriter(private val database: TrackyDatabase) {

    /**
     * Writes a whole server tree (projects, their tasks, those tasks' intervals and subtasks) in
     * one transaction.
     *
     * `GET /api/projects` returns everything the user owns, so this is what rehydrates a fresh
     * install. Rows are merged rather than blindly overwritten — see [serverWinsOnPull] — and
     * nothing is ever deleted: a local row the server does not know about is either still queued
     * for upload or was created offline, and must survive the pull either way.
     *
     * Interval rows additionally consult the outbox: [serverWinsOnPullForInterval] lets the server
     * close a locally-open interval, which is how a timer stopped on another device stops here,
     * unless this device still owes the server a change for that exact row. The pending ids are
     * read once up front rather than per row — this runs inside the transaction, and it is five
     * levels deep.
     *
     * Rows are written parents-first because Room enforces the foreign keys, and a row whose parent
     * is absent is skipped rather than inserted: one dangling reference throws inside the
     * transaction and would lose the *entire* pull, not just that row. The three oldest levels need
     * no such filter only because their parent is always in the same payload; subtasks and their
     * intervals do, and a subtask interval has to clear *both* of its parents.
     */
    suspend fun upsertServerTree(
        projects: List<ProjectEntity>,
        tasks: List<ProjectTaskEntity>,
        intervals: List<TaskIntervalEntity>,
        subTasks: List<ProjectSubTaskEntity> = emptyList(),
        subTaskIntervals: List<SubTaskIntervalEntity> = emptyList(),
    ) = inWriteTransaction {
        merge(ServerTreeRows(projects, tasks, intervals, subTasks, subTaskIntervals))
    }

    /**
     * Applies one page of the change feed: the upserts, then the deletions, in one transaction.
     *
     * Deletions are the reason this exists rather than a second call to [upsertServerTree].
     * That method promises never to delete, because in a full-tree pull an absent row is
     * ambiguous — it may have been created here and not pushed yet. A tombstone is not ambiguous,
     * it is the server stating a fact, so the promise can be kept in one place and broken in
     * another, deliberately.
     *
     * But only for rows this device does not still owe the server. A row recreated or edited
     * offline must outlive a tombstone the server emitted before it heard about the edit —
     * otherwise the pull destroys work the outbox is still carrying. The guard is the same outbox
     * [upsertServerTree] reads.
     *
     * Deleting a project cascades to its whole subtree locally, so a tombstone for a child that
     * arrives in the same page as its parent's is a no-op by the time it runs. That is fine and
     * is why the levels are deleted parents-first.
     */
    suspend fun applyDelta(upserts: ServerTreeRows, deletions: ServerTombstones) =
        inWriteTransaction {
            merge(upserts)
            delete(deletions)
        }

    private suspend fun inWriteTransaction(block: suspend () -> Unit) =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction { block() }
        }

    private suspend fun merge(tree: ServerTreeRows) {
        val pendingIntervalIds = database.pendingSyncDao.getPendingIntervalIds().toSet()

        mergeProjects(tree.projects)
        mergeTasks(tree.tasks)
        mergeIntervals(tree.intervals, pendingIntervalIds)
        mergeSubTasks(tree.subTasks)
        mergeSubTaskIntervals(tree.subTaskIntervals, pendingIntervalIds)
    }

    private suspend fun mergeProjects(projects: List<ProjectEntity>) {
        projects.forEach { incoming ->
            val local = database.projectDao.getProjectById(incoming.projectId)
            if (serverWinsOnPull(local?.updatedAtEpochMs, incoming.updatedAtEpochMs)) {
                database.projectDao.upsertProject(incoming)
            }
        }
    }

    private suspend fun mergeTasks(tasks: List<ProjectTaskEntity>) {
        tasks.forEach { incoming ->
            val local = database.projectDao.getTaskById(incoming.projectTaskId)
            if (serverWinsOnPull(local?.updatedAtEpochMs, incoming.updatedAtEpochMs)) {
                database.projectDao.upsertProjectTask(incoming)
            }
        }
    }

    private suspend fun mergeIntervals(intervals: List<TaskIntervalEntity>, pendingIntervalIds: Set<String>) {
        intervals.forEach { incoming ->
            val local = database.taskIntervalDao.getIntervalById(incoming.intervalId)
            val serverWins =
                local == null ||
                    serverWinsOnPullForInterval(
                        localEndDateTimeEpochMs = local.endDateTimeEpochMs,
                        serverEndDateTimeEpochMs = incoming.endDateTimeEpochMs,
                        hasPendingLocalPush = incoming.intervalId in pendingIntervalIds,
                    )
            if (serverWins) {
                // The server does carry startedByDeviceId, so the incoming value is preferred: a
                // row this device has never seen must keep the provenance of the device that
                // opened it, or an adopted foreign timer reads as one this device started.
                // Falling back to the local value covers a row the server still has null for -
                // every row predating the column - and stops a pull from making this device's own
                // open interval look foreign and unrecoverable.
                database.taskIntervalDao.upsertTaskInterval(
                    incoming.copy(
                        startedByDeviceId = incoming.startedByDeviceId ?: local?.startedByDeviceId,
                    ),
                )
            }
        }
    }

    private suspend fun mergeSubTasks(subTasks: List<ProjectSubTaskEntity>) {
        subTasks.forEach { incoming ->
            if (database.projectDao.getTaskById(incoming.parentProjectTaskId) == null) return@forEach
            val local = database.projectDao.getSubTaskById(incoming.projectSubTaskId)
            // A real stamp, exactly like a task's: subtasks are edited by hand.
            if (serverWinsOnPull(local?.updatedAtEpochMs, incoming.updatedAtEpochMs)) {
                database.projectDao.upsertProjectSubTask(incoming)
            }
        }
    }

    private suspend fun mergeSubTaskIntervals(
        subTaskIntervals: List<SubTaskIntervalEntity>,
        pendingIntervalIds: Set<String>,
    ) {
        subTaskIntervals.forEach { incoming ->
            // Two cascading parents, so two ways to dangle.
            if (database.projectDao.getSubTaskById(incoming.parentSubTaskId) == null) return@forEach
            if (database.taskIntervalDao.getIntervalById(incoming.parentTaskIntervalId) == null) return@forEach
            val local = database.projectDao.getSubTaskIntervalById(incoming.subTaskIntervalId)
            val serverWins =
                local == null ||
                    serverWinsOnPullForInterval(
                        localEndDateTimeEpochMs = local.endDateTimeEpochMs,
                        serverEndDateTimeEpochMs = incoming.endDateTimeEpochMs,
                        hasPendingLocalPush = incoming.subTaskIntervalId in pendingIntervalIds,
                    )
            if (serverWins) {
                // startedParentTimer has no wire counterpart, so the local value is kept to
                // preserve "stopping this subtask also stops its parent task"; a row this device
                // has never seen gets false, which is what it should have. startedByDeviceId does
                // travel, so the incoming value wins and only falls back to the local one for a
                // row the server still has null for.
                database.projectDao.upsertSubTaskInterval(
                    incoming.copy(
                        startedParentTimer = local?.startedParentTimer ?: false,
                        startedByDeviceId = incoming.startedByDeviceId ?: local?.startedByDeviceId,
                    ),
                )
            }
        }
    }

    private suspend fun delete(deletions: ServerTombstones) {
        val pending = database.pendingSyncDao.getAllPendingEntityIds().toSet()

        deletions.projectIds.forEach { if (it !in pending) database.projectDao.deleteProject(it) }
        deletions.taskIds.forEach { if (it !in pending) database.projectDao.deleteProjectTask(it) }
        deletions.intervalIds.forEach { if (it !in pending) database.taskIntervalDao.deleteTaskInterval(it) }
        deletions.subTaskIds.forEach { if (it !in pending) database.projectDao.deleteProjectSubTask(it) }
        deletions.subTaskIntervalIds.forEach { if (it !in pending) database.projectDao.deleteSubTaskInterval(it) }
    }
}
