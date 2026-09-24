package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.core.domain.sync.SyncChanges
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval

/**
 * Writes what the server sent into the local project tree, under the pull's merge rules: a full
 * pull, one page of the change feed, or the rows an active-timer call echoed back.
 */
interface LocalServerTreeDataSource {

    suspend fun upsertProjects(projects: List<Project>): EmptyResult<DataError.Local>

    /**
     * Applies one page of the change feed — the upserts and the tombstoned deletions — atomically.
     *
     * Separate from [upsertProjects] because that one promises never to delete, and it has to
     * keep promising it: in a full-tree pull an absent row may simply be one this device created
     * offline. A tombstone is the server stating a fact, which is a different thing entirely.
     */
    suspend fun applyDelta(changes: SyncChanges): EmptyResult<DataError.Local>

    /**
     * Writes the interval rows an active-timer call echoed back, under the same merge rules a pull
     * uses.
     *
     * Start and stop answer with every interval they touched, not just the one named — so the
     * device that superseded another device's timer learns the closing row here rather than up to
     * five minutes later, when the next delta happens to carry it.
     *
     * It goes through the pull merge on purpose: the echo is the server stating what it did, which
     * is exactly what a pulled row is, and both have to respect an unsent local change and keep a
     * row's provenance. Nothing is ever deleted, so this needs no tombstones.
     */
    suspend fun applyTimerEcho(
        taskIntervals: List<TaskInterval>,
        subTaskIntervals: List<SubTaskInterval>,
    ): EmptyResult<DataError.Local>
}
