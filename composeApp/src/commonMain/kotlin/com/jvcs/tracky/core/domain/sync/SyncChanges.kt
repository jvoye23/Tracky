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
    val tombstones: List<Tombstone>
) {
    /** True when there is nothing to write — the common case on a quiet poll. */
    val isEmpty: Boolean
        get() = projects.isEmpty() && tasks.isEmpty() && taskIntervals.isEmpty() &&
            subTasks.isEmpty() && subTaskIntervals.isEmpty() && tombstones.isEmpty()
}

/**
 * A row the server says was deleted.
 *
 * [entityType] is one of the `PendingSyncOperation.ENTITY_*` constants. An unrecognised value is
 * ignored rather than treated as an error: a newer server may know about a kind of row this build
 * does not, and refusing the whole delta over it would strand the device.
 */
data class Tombstone(
    val entityType: String,
    val entityId: String
)
