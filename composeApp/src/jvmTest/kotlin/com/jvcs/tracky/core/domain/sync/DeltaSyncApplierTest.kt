package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.FakeServerClockOffsetStore
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.ServerClock
import com.jvcs.tracky.features.project.data.project.OfflineFirstProjectRepository
import com.jvcs.tracky.features.project_tracker.data.FakeLocalProjectDataSource
import com.jvcs.tracky.features.project_tracker.data.FakePendingSyncDataSource
import com.jvcs.tracky.features.project_tracker.data.FakeRemoteProjectDataSource
import com.jvcs.tracky.features.project_tracker.data.FakeSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A scripted change feed: one queued response per call, and a record of the cursors asked for. */
private class FakeRemoteSyncDataSource(
    private val pages: MutableList<Result<SyncChanges, DataError.Remote>> = mutableListOf(),
) : RemoteSyncDataSource {

    val requestedCursors = mutableListOf<Long?>()

    fun enqueue(vararg page: Result<SyncChanges, DataError.Remote>) = apply { pages += page }

    override suspend fun getChanges(since: Long?): Result<SyncChanges, DataError.Remote> {
        requestedCursors += since
        return if (pages.isEmpty()) {
            Result.Success(changes(cursor = since ?: 0))
        } else {
            pages.removeAt(0)
        }
    }
}

private fun changes(
    cursor: Long,
    hasMore: Boolean = false,
    fullResyncRequired: Boolean = false,
    tombstones: List<Tombstone> = emptyList(),
    serverNow: Instant? = null,
) = SyncChanges(
    cursor = cursor,
    serverNow = serverNow,
    fullResyncRequired = fullResyncRequired,
    hasMore = hasMore,
    projects = emptyList(),
    tasks = emptyList(),
    taskIntervals = emptyList(),
    subTasks = emptyList(),
    subTaskIntervals = emptyList(),
    tombstones = tombstones,
)

internal class DeltaSyncApplierTest {

    private val local = FakeLocalProjectDataSource()
    private val remoteProjects = FakeRemoteProjectDataSource()
    private val cursorStore = FakeSyncCursorStore()
    private val timeProvider = FakeTimeProvider()
    private val offsetStore = FakeServerClockOffsetStore()
    private val serverClock = ServerClock(timeProvider, offsetStore)
    private val syncRecency = SyncRecency()

    private fun applier(remote: FakeRemoteSyncDataSource) =
        DeltaSyncApplier(
            remoteSyncDataSource = remote,
            localProjectDataSource = local,
            projectRepository =
                OfflineFirstProjectRepository(
                    localProjectDataSource = local,
                    remoteProjectDataSource = remoteProjects,
                    pendingSyncDataSource = FakePendingSyncDataSource(),
                    syncScheduler = FakeSyncScheduler(),
                    applicationScope = CoroutineScope(Dispatchers.Unconfined),
                    timeProvider = timeProvider,
                ),
            syncCursorStore = cursorStore,
            serverClock = serverClock,
            timeProvider = timeProvider,
            syncRecency = syncRecency,
        )

    @Test
    fun theFirstPullAsksForEverything() =
        runTest {
            val remote = FakeRemoteSyncDataSource().enqueue(Result.Success(changes(cursor = 12)))

            applier(remote).pullChanges()

            // Null rather than 0: the server reads a missing `since` as "everything, no tombstones".
            assertEquals(listOf<Long?>(null), remote.requestedCursors)
            assertEquals(12L, cursorStore.cursor())
        }

    @Test
    fun theCursorAdvancesOnlyAfterThePageLands() =
        runTest {
            val remote =
                FakeRemoteSyncDataSource().enqueue(
                    Result.Success(changes(cursor = 12, tombstones = listOf(Tombstone("project", "p1")))),
                )

            applier(remote).pullChanges()

            assertEquals(1, local.applyDeltaCalls)
            assertEquals(12L, cursorStore.cursor())
        }

    @Test
    fun aFailedWriteLeavesTheCursorWhereItWas() =
        runTest {
            cursorStore.setCursor(5)
            local.failApplyDelta = true
            val remote =
                FakeRemoteSyncDataSource().enqueue(
                    Result.Success(changes(cursor = 12, tombstones = listOf(Tombstone("project", "p1")))),
                )

            val result = applier(remote).pullChanges()

            // Advancing past a page that did not land would skip it on every later pull, for good.
            assertTrue(result is Result.Error)
            assertEquals(5L, cursorStore.cursor())
        }

    @Test
    fun aTransientFailureLeavesTheCursorWhereItWas() =
        runTest {
            cursorStore.setCursor(5)
            val remote = FakeRemoteSyncDataSource().enqueue(Result.Error(DataError.Remote.NO_INTERNET))

            val result = applier(remote).pullChanges()

            assertTrue(result is Result.Error)
            assertEquals(5L, cursorStore.cursor())
            assertTrue(remoteProjects.getProjectsCallCount == 0)
        }

    @Test
    fun itFollowsHasMoreToTheEndOfTheFeed() =
        runTest {
            val remote =
                FakeRemoteSyncDataSource().enqueue(
                    Result.Success(changes(cursor = 10, hasMore = true)),
                    Result.Success(changes(cursor = 20, hasMore = true)),
                    Result.Success(changes(cursor = 30, hasMore = false)),
                )

            applier(remote).pullChanges()

            // One call leaves the device current, not one page behind.
            assertEquals(listOf<Long?>(null, 10, 20), remote.requestedCursors)
            assertEquals(30L, cursorStore.cursor())
        }

    @Test
    fun aServerThatSetsHasMoreWithoutAdvancingDoesNotSpin() =
        runTest {
            cursorStore.setCursor(10)
            val remote =
                FakeRemoteSyncDataSource().enqueue(
                    Result.Success(changes(cursor = 10, hasMore = true)),
                    Result.Success(changes(cursor = 10, hasMore = true)),
                )

            applier(remote).pullChanges()

            assertEquals(1, remote.requestedCursors.size)
        }

    @Test
    fun anExpiredCursorFallsBackToAFullPullAndForgetsIt() =
        runTest {
            cursorStore.setCursor(5)
            val remote =
                FakeRemoteSyncDataSource().enqueue(
                    Result.Success(changes(cursor = 5, fullResyncRequired = true)),
                )

            applier(remote).pullChanges()

            // Whatever the cursor pointed at is exactly what could not be trusted, so it goes.
            assertEquals(1, remoteProjects.getProjectsCallCount)
            assertNull(cursorStore.cursor())
        }

    @Test
    fun aDeploymentWithoutTheEndpointFallsBackToAFullPull() =
        runTest {
            val remote = FakeRemoteSyncDataSource().enqueue(Result.Error(DataError.Remote.NOT_FOUND))

            val result = applier(remote).pullChanges()

            // This is what makes the client shippable ahead of the backend: no feed, no problem,
            // just the old full pull.
            assertTrue(result is Result.Success)
            assertEquals(1, remoteProjects.getProjectsCallCount)
        }

    @Test
    fun anEmptyPageWritesNothingButStillAdvances() =
        runTest {
            val remote = FakeRemoteSyncDataSource().enqueue(Result.Success(changes(cursor = 42)))

            applier(remote).pullChanges()

            // The common case on a quiet account: no transaction, but the cursor moves so the next
            // poll stays cheap.
            assertEquals(0, local.applyDeltaCalls)
            assertEquals(42L, cursorStore.cursor())
        }

    @Test
    fun everyResponseIsAClockSample() =
        runTest {
            val serverNow = timeProvider.nowInstant + 40.seconds
            val remote =
                FakeRemoteSyncDataSource()
                    .enqueue(Result.Success(changes(cursor = 12, serverNow = serverNow)))

            applier(remote).pullChanges()

            // The timer derives elapsed as now - startedAt, and startedAt may have come from another
            // device. Forty seconds of skew is forty seconds of tracked time that does not exist.
            assertEquals(40_000L, offsetStore.offsetMillis())
        }

    @Test
    fun aFullResyncResponseStillCarriesTheClock() =
        runTest {
            val serverNow = timeProvider.nowInstant + 40.seconds
            val remote =
                FakeRemoteSyncDataSource().enqueue(
                    Result.Success(changes(cursor = 5, fullResyncRequired = true, serverNow = serverNow)),
                )

            applier(remote).pullChanges()

            assertEquals(40_000L, offsetStore.offsetMillis())
        }

    /**
     * Recency is stamped here rather than by the caller, so that every route to a pull counts the
     * same. It used to be stamped only by the sync loop, which meant a pull-to-refresh brought
     * fresh rows in while leaving the timer believing it had not synced since launch -- and a
     * foreign timer froze as stale fifteen minutes later regardless.
     */
    @Test
    fun aPullThatLandedCountsAsHearingFromTheServer() =
        runTest {
            val remote = FakeRemoteSyncDataSource().enqueue(Result.Success(changes(cursor = 7)))

            applier(remote).pullChanges()

            assertEquals(timeProvider.nowInstant, syncRecency.lastSuccessfulSync.value)
        }

    @Test
    fun aPullThatFailedDoesNot() =
        runTest {
            val remote =
                FakeRemoteSyncDataSource()
                    .enqueue(Result.Error(DataError.Remote.NO_INTERNET))

            applier(remote).pullChanges()

            assertNull(syncRecency.lastSuccessfulSync.value)
        }
}
