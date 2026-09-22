@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.core.domain.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.jvcs.tracky.core.domain.connectivity.ConnectivityObserver
import com.jvcs.tracky.core.domain.lifecycle.AppLifecycleObserver
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Counts drains of the push queue. */
private class RecordingSyncRepository : SyncRepository {
    var drains = 0
        private set

    override suspend fun syncPendingOperations() {
        drains++
    }
}

/** A feed that always answers, and counts how many times it was asked. */
private class CountingRemoteSyncDataSource(
    private var failures: Int = 0
) : RemoteSyncDataSource {

    var calls = 0
        private set

    override suspend fun getChanges(since: Long?): Result<SyncChanges, DataError.Remote> {
        calls++
        if (failures > 0) {
            failures--
            return Result.Error(DataError.Remote.NO_INTERNET)
        }
        return Result.Success(
            SyncChanges(
                cursor = since ?: 0,
                serverNow = null,
                fullResyncRequired = false,
                hasMore = false,
                projects = emptyList(),
                tasks = emptyList(),
                taskIntervals = emptyList(),
                subTasks = emptyList(),
                subTaskIntervals = emptyList(),
                tombstones = emptyList()
            )
        )
    }
}

/**
 * The JVM actuals of [ConnectivityObserver] and [AppLifecycleObserver] are both `flowOf(true)` —
 * always online, always foregrounded. That is exactly the device-B case these tests are about: an
 * app sitting open while another device changes something.
 */
internal class ProjectSyncManagerTest {

    private val timeProvider = FakeTimeProvider()
    private val syncRepository = RecordingSyncRepository()
    private val syncRecency = SyncRecency()

    private fun manager(
        remote: CountingRemoteSyncDataSource,
        scope: CoroutineScope
    ): ProjectSyncManager {
        val local = FakeLocalProjectDataSource()
        return ProjectSyncManager(
            connectivityObserver = ConnectivityObserver(),
            appLifecycleObserver = AppLifecycleObserver(),
            syncRepository = syncRepository,
            deltaSyncApplier = DeltaSyncApplier(
                remoteSyncDataSource = remote,
                localProjectDataSource = local,
                projectRepository = OfflineFirstProjectRepository(
                    localProjectDataSource = local,
                    remoteProjectDataSource = FakeRemoteProjectDataSource(),
                    pendingSyncDataSource = FakePendingSyncDataSource(),
                    syncScheduler = FakeSyncScheduler(),
                    applicationScope = CoroutineScope(Dispatchers.Unconfined),
                    timeProvider = timeProvider
                ),
                syncCursorStore = FakeSyncCursorStore(),
                serverClock = ServerClock(timeProvider, FakeServerClockOffsetStore()),
                timeProvider = timeProvider
            ),
            syncRecency = syncRecency,
            applicationScope = scope,
            timeProvider = timeProvider
        )
    }

    /**
     * The regression this class exists for. A device that stays online and foregrounded used to
     * pull exactly once, at launch, and then never again: the trigger was
     * `distinctUntilChanged().filter { it }` over a Boolean, which emits only on the false-to-true
     * edge. Nothing in the chain was a clock, so the five-minute constant was a ceiling on an
     * event that never re-fired. Device B therefore never learned about device A.
     */
    @Test
    fun keepsPullingWhileTheAppStaysOnlineAndForegrounded() = runTest {
        val remote = CountingRemoteSyncDataSource()
        manager(remote, backgroundScope).start()

        advanceTimeBy(90.seconds)

        // Launch, plus one per 30s tick. The exact count matters less than "more than one".
        assertTrue(
            remote.calls >= 3,
            "expected repeated pulls while foregrounded, but the feed was asked ${remote.calls} time(s)"
        )
    }

    @Test
    fun drainsThePushQueueOnEveryTickToo() = runTest {
        manager(CountingRemoteSyncDataSource(), backgroundScope).start()

        advanceTimeBy(90.seconds)

        assertTrue(
            syncRepository.drains >= 3,
            "expected the outbox to drain on every tick, but it drained ${syncRepository.drains} time(s)"
        )
    }

    /**
     * `lastPull` used to be stamped outside the success check, so one failed pull burned the whole
     * throttle window. With the tick as the retry interval, a failure must simply be retried.
     */
    @Test
    fun retriesOnTheNextTickAfterAFailedPull() = runTest {
        val remote = CountingRemoteSyncDataSource(failures = 1)
        manager(remote, backgroundScope).start()

        advanceTimeBy(90.seconds)

        assertTrue(
            remote.calls >= 3,
            "a failed pull must not burn the window, but the feed was asked ${remote.calls} time(s)"
        )
        assertTrue(
            syncRecency.lastSuccessfulSync.value != null,
            "a later successful pull should still stamp SyncRecency"
        )
    }

    /** Only a pull that landed counts as hearing from the server. */
    @Test
    fun doesNotStampRecencyWhileEveryPullFails() = runTest {
        val remote = CountingRemoteSyncDataSource(failures = Int.MAX_VALUE)
        manager(remote, backgroundScope).start()

        advanceTimeBy(90.seconds)

        assertEquals(null, syncRecency.lastSuccessfulSync.value)
    }

    @Test
    fun startIsIdempotent() = runTest {
        val remote = CountingRemoteSyncDataSource()
        val manager = manager(remote, backgroundScope)

        manager.start()
        manager.start()
        advanceTimeBy(100.milliseconds)
        val afterOneLaunch = remote.calls

        assertEquals(1, afterOneLaunch, "a second start() must not add a second polling loop")
    }

    /** The cadence is a safety net, not a busy loop. */
    @Test
    fun doesNotPullFasterThanTheCadence() = runTest {
        val remote = CountingRemoteSyncDataSource()
        manager(remote, backgroundScope).start()

        advanceTimeBy(5.minutes)

        assertTrue(
            remote.calls <= 12,
            "expected roughly one pull per 30s, but the feed was asked ${remote.calls} time(s)"
        )
    }
}
