package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.isTransient
import com.jvcs.tracky.features.project.domain.project.LocalProjectDataSource
import com.jvcs.tracky.features.project.domain.project.ProjectRepository

/**
 * Pulls the server's change feed and applies it, falling back to the full-tree pull when the feed
 * cannot be used.
 *
 * Three things have to be true of this, and each is a way to corrupt a user's data if it is not:
 *
 * The cursor advances **only after** the page has landed. Advancing first and failing halfway
 * would skip the rest of that change set on every subsequent pull, permanently.
 *
 * A page is applied **whole or not at all**, which is `ProjectDao.applyDelta`'s transaction.
 *
 * And the fallback is unconditional whenever the feed is unusable — an expired cursor, or a
 * deployment that does not have the endpoint yet. A device must never be left with no way to
 * reach the server's state.
 */
class DeltaSyncApplier(
    private val remoteSyncDataSource: RemoteSyncDataSource,
    private val localProjectDataSource: LocalProjectDataSource,
    private val projectRepository: ProjectRepository,
    private val syncCursorStore: SyncCursorStore
) {

    /**
     * Brings local state up to date, by delta when possible and by full pull when not.
     *
     * Follows `hasMore` to the end of the feed, so one call always leaves the device current
     * rather than one page behind.
     */
    suspend fun pullChanges(): EmptyResult<DataError> {
        var pagesApplied = 0
        while (true) {
            val since = syncCursorStore.cursor()

            val changes = when (val result = remoteSyncDataSource.getChanges(since)) {
                is Result.Success -> result.data
                is Result.Error -> return when {
                    // The endpoint is not deployed yet. The full pull is still correct, just
                    // more expensive, so this degrades rather than fails.
                    result.error == DataError.Remote.NOT_FOUND -> fullPull()
                    result.error.isTransient() -> Result.Error(result.error)
                    else -> Result.Error(result.error)
                }
            }

            // The server cannot prove what was deleted since our cursor, so carrying on from it
            // would leave this device quietly diverged.
            if (changes.fullResyncRequired) return fullPull()

            if (!changes.isEmpty) {
                val applied = localProjectDataSource.applyDelta(changes)
                if (applied is Result.Error) return Result.Error(applied.error)
            }

            // Only now, and never before: the page is in Room.
            syncCursorStore.setCursor(changes.cursor)
            pagesApplied++

            if (!changes.hasMore) break
            // A server that sets hasMore without advancing the cursor would spin here forever.
            if (since != null && changes.cursor <= since) break
        }
        return Result.Success(Unit)
    }

    /**
     * Re-reads the whole tree and forgets the cursor.
     *
     * The cursor is cleared rather than left in place because whatever it pointed at is exactly
     * what we could not trust. The next delta starts from scratch, which is correct if wasteful;
     * keeping a cursor the server has disowned is neither.
     */
    private suspend fun fullPull(): EmptyResult<DataError> {
        syncCursorStore.clear()
        return projectRepository.fetchProjects()
    }
}
