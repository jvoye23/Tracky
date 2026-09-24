@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.core.domain.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class SyncPullCoordinatorTest {

    private val remote = CountingRemoteSyncDataSource()

    private fun coordinator(scope: kotlinx.coroutines.CoroutineScope) =
        SyncPullCoordinator(
            deltaSyncApplier = testDeltaSyncApplier(remote = remote),
            applicationScope = scope,
            coalesceWindow = 200.milliseconds,
        )

    /**
     * The reason this class exists. Concurrent pulls both read the cursor before either advances
     * it, which can walk the cursor backwards; serialising them is the fix, and a burst collapsing
     * into one round trip is the reason it is worth having rather than a bare Mutex at each caller.
     */
    @Test
    fun aBurstOfRequestsCostsOnePull() =
        runTest {
            val coordinator = coordinator(backgroundScope)

            repeat(10) { coordinator.request() }
            advanceTimeBy(1.seconds)
            advanceUntilIdle()

            assertEquals(1, remote.calls)
        }

    @Test
    fun requestsArrivingAfterAPullHasRunEarnAnotherOne() =
        runTest {
            val coordinator = coordinator(backgroundScope)

            coordinator.request()
            advanceTimeBy(1.seconds)
            advanceUntilIdle()
            coordinator.request()
            advanceTimeBy(1.seconds)
            advanceUntilIdle()

            assertEquals(2, remote.calls)
        }

    /** Two callers asking at once must not have their pulls interleave. */
    @Test
    fun concurrentPullsAreSerialised() =
        runTest {
            val coordinator = coordinator(backgroundScope)

            val a = backgroundScope.async { coordinator.pullNow() }
            val b = backgroundScope.async { coordinator.pullNow() }
            a.await()
            b.await()
            advanceUntilIdle()

            // Both ran — nothing was dropped — and the feed was never asked by two callers at once.
            assertEquals(2, remote.calls)
            assertTrue(remote.maxConcurrent <= 1, "two pulls overlapped: ${remote.maxConcurrent}")
        }

    @Test
    fun aCoalescedRequestAndAnAwaitedPullDoNotOverlapEither() =
        runTest {
            val coordinator = coordinator(backgroundScope)

            coordinator.request()
            coordinator.pullNow()
            advanceTimeBy(1.seconds)
            advanceUntilIdle()

            assertTrue(remote.maxConcurrent <= 1, "two pulls overlapped: ${remote.maxConcurrent}")
        }

    /** Nothing asked, nothing pulled: the coordinator is not itself a clock. */
    @Test
    fun doesNothingUntilAsked() =
        runTest {
            coordinator(backgroundScope)

            advanceTimeBy(10.seconds)
            advanceUntilIdle()

            assertEquals(0, remote.calls)
        }
}
