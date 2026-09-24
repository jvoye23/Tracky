package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.ServerClock
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.domain.project.LocalServerTreeDataSource
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import kotlin.time.Instant

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
    private val localServerTreeDataSource: LocalServerTreeDataSource,
    private val projectRepository: ProjectRepository,
    private val syncCursorStore: SyncCursorStore,
    private val serverClock: ServerClock,
    private val timeProvider: TimeProvider,
    private val syncRecency: SyncRecency,
) {

    /**
     * Brings local state up to date, by delta when possible and by full pull when not.
     *
     * Follows `hasMore` to the end of the feed, so one call always leaves the device current
     * rather than one page behind.
     *
     * Stamping [SyncRecency] belongs here rather than in the callers. It used to live in
     * [ProjectSyncManager], which made "we heard from the server" mean "the sync loop heard from
     * the server": a pull-to-refresh brought fresh data in and left the timer believing it had not
     * synced since launch, so a foreign timer froze as stale fifteen minutes later however recently
     * the user had refreshed. One pull, one stamp, wherever the pull came from.
     */
    suspend fun pullChanges(): EmptyResult<DataError> {
        val result = pull()
        // Only a pull that landed counts. Stamping the attempt would keep a foreign timer ticking
        // through an outage, which is the one thing SyncRecency exists to stop.
        if (result is Result.Success) syncRecency.markSynced(timeProvider.nowInstant)
        return result
    }

    private suspend fun pull(): EmptyResult<DataError> {
        var result: EmptyResult<DataError>? = null
        while (result == null) {
            result = applyNextPage()
        }
        return result
    }

    /**
     * Fetches and applies the page after the stored cursor.
     *
     * Null means there is another page to fetch. Anything else is the result of the whole pull.
     */
    private suspend fun applyNextPage(): EmptyResult<DataError>? {
        val since = syncCursorStore.cursor()

        val sentAt = timeProvider.nowInstant
        val changes =
            when (val result = remoteSyncDataSource.getChanges(since)) {
                is Result.Success -> result.data

                // A NOT_FOUND means the endpoint is not deployed yet. The full pull is still
                // correct, just more expensive, so this degrades rather than fails.
                is Result.Error -> return if (result.error == DataError.Remote.NOT_FOUND) {
                    fullPull()
                } else {
                    Result.Error(result.error)
                }
            }

        // Every response is a clock sample, including one that asks for a full resync — the
        // timer wants the offset whatever else happened.
        //
        // Halfway through the round trip, not the moment the response arrived: the server's
        // instant was true somewhere in the middle, and crediting the whole latency to skew
        // would bias the offset by however slow the network was. That mattered little while
        // the offset only moved a rendered number; it matters now that interval timestamps
        // are written on it.
        changes.serverNow?.let { serverClock.observe(it, midpoint(sentAt, timeProvider.nowInstant)) }

        // The server cannot prove what was deleted since our cursor, so carrying on from it
        // would leave this device quietly diverged.
        return if (changes.fullResyncRequired) fullPull() else storePage(changes, since)
    }

    /** Applies one page and advances the cursor past it. Null means there is another page. */
    private suspend fun storePage(changes: SyncChanges, since: Long?): EmptyResult<DataError>? {
        if (!changes.isEmpty) {
            val applied = localServerTreeDataSource.applyDelta(changes)
            if (applied is Result.Error) return Result.Error(applied.error)
        }

        // Only now, and never before: the page is in Room.
        syncCursorStore.setCursor(changes.cursor)

        // A server that sets hasMore without advancing the cursor would spin here forever.
        val hasNextPage = changes.hasMore && (since == null || changes.cursor > since)
        return if (hasNextPage) null else Result.Success(Unit)
    }

    /** The instant halfway between a request leaving and its answer arriving. */
    private fun midpoint(sentAt: Instant, receivedAt: Instant): Instant = sentAt + (receivedAt - sentAt) / 2

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
