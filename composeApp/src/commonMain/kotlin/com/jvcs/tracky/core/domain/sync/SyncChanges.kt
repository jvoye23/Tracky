package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlin.time.Instant

/**
 * One page of the server's change feed.
 *
 * Flat rather than a tree: a delta carries whatever changed, which routinely means a task whose
 * project did not change. The applier writes them parents-first and skips any row whose parent it
 * does not have, exactly as the full-tree pull already does.
 */
data class SyncChanges(
    val cursor: Long,
    val serverNow: Instant?,
    /**
     * The server could not prove what was deleted since the requested cursor, because it predates
     * tombstone retention. The client must fall back to a full pull rather than carry on from a
     * cursor it cannot trust.
     */
    val fullResyncRequired: Boolean,
    val hasMore: Boolean,
    val projects: List<Project>,
    val tasks: List<ProjectTask>,
    val taskIntervals: List<TaskInterval>,
    val subTasks: List<ProjectSubTask>,
    val subTaskIntervals: List<SubTaskInterval>,
    val tombstones: List<Tombstone>,
) {
    /** True when there is nothing to write — the common case on a quiet poll. */
    val isEmpty: Boolean
        get() =
            projects.isEmpty() &&
                tasks.isEmpty() &&
                taskIntervals.isEmpty() &&
                subTasks.isEmpty() &&
                subTaskIntervals.isEmpty() &&
                tombstones.isEmpty()
}

/**
 * A row the server says was deleted.
 *
 * An unrecognised [entityType] is ignored rather than treated as an error: a newer server may know
 * about a kind of row this build does not, and refusing the whole delta over it would strand the
 * device.
 */
data class Tombstone(val entityType: String, val entityId: String) {
    /**
     * The server's vocabulary for [entityType], transcribed from `backend-delta-sync-api.md` §3.
     *
     * Deliberately **not** the `PendingSyncOperation.ENTITY_*` constants, which this used to be
     * documented as. Those are local Room table names, they are persisted in
     * `pending_sync_operations` and therefore cannot be renamed, and they disagree with the wire on
     * exactly two of the five levels: the server says `task` where the outbox says `project_task`,
     * and `sub_task` where it says `project_sub_task`. Matching tombstones against them meant every
     * task and subtask deleted on one device was silently dropped on every other — the lookup
     * missed, and a missed lookup is indistinguishable from a type this build does not know.
     *
     * The two vocabularies coincide on `project`, `task_interval` and `sub_task_interval`, which is
     * what made the mistake survive: every tombstone fixture in the test suite happened to use
     * `project`, the one level where being wrong is invisible.
     */
    companion object {
        const val PROJECT = "project"
        const val TASK = "task"
        const val TASK_INTERVAL = "task_interval"
        const val SUB_TASK = "sub_task"
        const val SUB_TASK_INTERVAL = "sub_task_interval"
    }
}
