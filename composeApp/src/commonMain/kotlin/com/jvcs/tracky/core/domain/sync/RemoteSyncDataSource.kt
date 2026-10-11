package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project

/** Reads the server's change feed, and takes the one-shot upload of a device's local-only data. */
interface RemoteSyncDataSource {

    /**
     * One page of changes above [since], or everything the user owns when [since] is null.
     *
     * Returns [DataError.Remote.NOT_FOUND] on a deployment that does not have the endpoint yet;
     * the caller falls back to the full-tree pull, which is what keeps this shippable ahead of
     * the backend.
     */
    suspend fun getChanges(since: Long?): Result<SyncChanges, DataError.Remote>

    /**
     * Uploads [projects] with their whole trees into the signed-in account, in one transaction.
     *
     * Idempotent: an id the account already has is skipped, never overwritten, so a call whose
     * response was lost can simply be made again.
     */
    suspend fun importProjects(projects: List<Project>): EmptyResult<DataError.Remote>
}
