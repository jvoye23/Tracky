@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.core.domain.sync

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.connectivity.FakeConnectivityObserver
import com.jvcs.tracky.core.domain.lifecycle.FakeAppLifecycleObserver
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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

/**
 * The connectivity and lifecycle fakes default to online and foregrounded. That is exactly the device-B case these tests are about: an
 * app sitting open while another device changes something.
 */
internal class ProjectSyncManagerTest {

    private val timeProvider = FakeTimeProvider()
    private val syncRepository = RecordingSyncRepository()
    private val syncRecency = SyncRecency()

    private fun manager(remote: CountingRemoteSyncDataSource, scope: CoroutineScope) =
        ProjectSyncManager(
            connectivityObserver = FakeConnectivityObserver(),
            appLifecycleObserver = FakeAppLifecycleObserver(),
            syncRepository = syncRepository,
            pullCoordinator =
                SyncPullCoordinator(
                    deltaSyncApplier =
                        testDeltaSyncApplier(
                            remote = remote,
                            timeProvider = timeProvider,
                            syncRecency = syncRecency,
                        ),
                    applicationScope = scope,
                ),
            applicationScope = scope,
        )

    /**
     * The regression this class exists for. A device that stays online and foregrounded used to
     * pull exactly once, at launch, and then never again: the trigger was
     * `distinctUntilChanged().filter { it }` over a Boolean, which emits only on the false-to-true
     * edge. Nothing in the chain was a clock, so the five-minute constant was a ceiling on an
     * event that never re-fired. Device B therefore never learned about device A.
     */
    @Test
    fun keepsPullingWhileTheAppStaysOnlineAndForegrounded() =
        runTest {
            val remote = CountingRemoteSyncDataSource()
            manager(remote, backgroundScope).start()

            advanceTimeBy(90.seconds)

            // Launch, plus one per 30s tick. The exact count matters less than "more than one".
            assertThat(
                remote.calls >= 3,
                name = "expected repeated pulls while foregrounded, but the feed was asked ${remote.calls} time(s)",
            ).isTrue()
        }

    @Test
    fun drainsThePushQueueOnEveryTickToo() =
        runTest {
            manager(CountingRemoteSyncDataSource(), backgroundScope).start()

            advanceTimeBy(90.seconds)

            assertThat(
                syncRepository.drains >= 3,
                name = "expected the outbox to drain on every tick, but it drained ${syncRepository.drains} time(s)",
            ).isTrue()
        }

    /**
     * `lastPull` used to be stamped outside the success check, so one failed pull burned the whole
     * throttle window. With the tick as the retry interval, a failure must simply be retried.
     */
    @Test
    fun retriesOnTheNextTickAfterAFailedPull() =
        runTest {
            val remote = CountingRemoteSyncDataSource(failures = 1)
            manager(remote, backgroundScope).start()

            advanceTimeBy(90.seconds)

            assertThat(
                remote.calls >= 3,
                name = "a failed pull must not burn the window, but the feed was asked ${remote.calls} time(s)",
            ).isTrue()
            assertThat(
                syncRecency.lastSuccessfulSync.value != null,
                name = "a later successful pull should still stamp SyncRecency",
            ).isTrue()
        }

    /** Only a pull that landed counts as hearing from the server. */
    @Test
    fun doesNotStampRecencyWhileEveryPullFails() =
        runTest {
            val remote = CountingRemoteSyncDataSource(failures = Int.MAX_VALUE)
            manager(remote, backgroundScope).start()

            advanceTimeBy(90.seconds)

            assertThat(syncRecency.lastSuccessfulSync.value).isEqualTo(null)
        }

    @Test
    fun startIsIdempotent() =
        runTest {
            val remote = CountingRemoteSyncDataSource()
            val manager = manager(remote, backgroundScope)

            manager.start()
            manager.start()
            advanceTimeBy(100.milliseconds)
            val afterOneLaunch = remote.calls

            assertThat(afterOneLaunch, name = "a second start() must not add a second polling loop").isEqualTo(1)
        }

    /** The cadence is a safety net, not a busy loop. */
    @Test
    fun doesNotPullFasterThanTheCadence() =
        runTest {
            val remote = CountingRemoteSyncDataSource()
            manager(remote, backgroundScope).start()

            advanceTimeBy(5.minutes)

            assertThat(
                remote.calls <= 12,
                name = "expected roughly one pull per 30s, but the feed was asked ${remote.calls} time(s)",
            ).isTrue()
        }
}
