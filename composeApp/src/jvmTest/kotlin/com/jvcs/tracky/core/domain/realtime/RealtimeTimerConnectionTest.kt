@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.core.domain.realtime

import com.jvcs.tracky.core.data.networking.dto.RealtimeEnvelopeParser
import com.jvcs.tracky.core.domain.device.FakeDeviceIdProvider
import com.jvcs.tracky.core.domain.sync.CountingRemoteSyncDataSource
import com.jvcs.tracky.core.domain.sync.FakeSyncCursorStore
import com.jvcs.tracky.core.domain.sync.SyncPullCoordinator
import com.jvcs.tracky.core.domain.sync.testDeltaSyncApplier
import com.jvcs.tracky.core.domain.util.DataError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class RealtimeTimerConnectionTest {

    private val channel = FakeRealtimeChannel()
    private val remote = CountingRemoteSyncDataSource()
    private val cursorStore = FakeSyncCursorStore()
    private val connectivity = RealtimeConnectivity()

    private val online = MutableStateFlow(true)
    private val foreground = MutableStateFlow(true)
    private val authenticated = MutableStateFlow(true)

    private fun connection(scope: CoroutineScope, random: Random = Random(1)) =
        RealtimeTimerConnection(
            channel = channel,
            parser = RealtimeEnvelopeParser(Json { ignoreUnknownKeys = true }),
            deviceIdProvider = FakeDeviceIdProvider(),
            syncCursorStore = cursorStore,
            pullCoordinator = SyncPullCoordinator(
                deltaSyncApplier = testDeltaSyncApplier(remote = remote, cursorStore = cursorStore),
                applicationScope = scope,
                coalesceWindow = 10.milliseconds
            ),
            connectivity = connectivity,
            json = Json { ignoreUnknownKeys = true },
            isOnline = online,
            isInForeground = foreground,
            isAuthenticated = authenticated,
            applicationScope = scope,
            random = random
        )

    /** Past the 1s connectivity debounce, then let the handshake settle. */
    private fun settleConnect(scope: kotlinx.coroutines.test.TestScope) {
        scope.advanceTimeBy(2.seconds)
        scope.advanceUntilIdle()
    }

    // --- the gate ----------------------------------------------------------------------------

    @Test
    fun connectsWhenOnlineForegroundedAndSignedIn() = runTest {
        connection(backgroundScope).start()
        settleConnect(this)

        assertEquals(1, channel.opens)
        assertEquals(RealtimeConnectionState.Connected, connectivity.state.value)
    }

    @Test
    fun doesNotConnectWhileBackgrounded() = runTest {
        foreground.value = false
        connection(backgroundScope).start()
        settleConnect(this)

        assertEquals(0, channel.opens)
    }

    @Test
    fun doesNotConnectWhileSignedOut() = runTest {
        authenticated.value = false
        connection(backgroundScope).start()
        settleConnect(this)

        assertEquals(0, channel.opens)
    }

    @Test
    fun closesTheSocketWhenTheGateShuts() = runTest {
        connection(backgroundScope).start()
        settleConnect(this)
        val session = channel.current

        foreground.value = false
        // Past the connectivity debounce, which keeps the gate's combine busy for a moment.
        advanceTimeBy(3.seconds)
        advanceUntilIdle()

        assertTrue(session.closed, "backgrounding must tear the socket down, not leave it open")
    }

    // --- the handshake -----------------------------------------------------------------------

    @Test
    fun sendsExactlyOneHelloCarryingTheDeviceAndCursor() = runTest {
        cursorStore.setCursor(84213)
        connection(backgroundScope).start()
        settleConnect(this)

        assertEquals(1, channel.current.sent.size)
        val hello = channel.current.sent.single()
        assertTrue(hello.contains(""""type":"hello""""), hello)
        assertTrue(hello.contains(""""cursor":84213"""), hello)
    }

    /** Zero rather than absent, so the server's "are you behind" comparison is true. */
    @Test
    fun aDeviceThatHasNeverPulledAnnouncesCursorZero() = runTest {
        connection(backgroundScope).start()
        settleConnect(this)

        assertTrue(channel.current.sent.single().contains(""""cursor":0"""))
    }

    // --- envelopes become pulls --------------------------------------------------------------

    @Test
    fun anInvalidateTriggersAPull() = runTest {
        connection(backgroundScope).start()
        settleConnect(this)
        val before = remote.calls

        channel.current.emit("""{"type":"invalidate","cursor":9}""")
        advanceTimeBy(1.seconds)
        advanceUntilIdle()

        assertEquals(before + 1, remote.calls)
    }

    @Test
    fun aTimerEnvelopeTriggersAPullToo() = runTest {
        connection(backgroundScope).start()
        settleConnect(this)
        val before = remote.calls

        channel.current.emit("""{"type":"timer","cursor":9,"active":null}""")
        advanceTimeBy(1.seconds)
        advanceUntilIdle()

        assertEquals(before + 1, remote.calls)
    }

    /** A future backend addition must not be able to break an old client. */
    @Test
    fun anUnknownEnvelopeTypeStillTriggersAPullAndKeepsTheSocket() = runTest {
        connection(backgroundScope).start()
        settleConnect(this)
        val before = remote.calls

        channel.current.emit("""{"type":"somethingNewInV2"}""")
        advanceTimeBy(1.seconds)
        advanceUntilIdle()

        assertEquals(before + 1, remote.calls)
        assertEquals(1, channel.opens, "an unknown type must not drop the connection")
    }

    @Test
    fun anUnreadableFrameIsIgnoredAndTheSocketSurvives() = runTest {
        connection(backgroundScope).start()
        settleConnect(this)
        val before = remote.calls

        channel.current.emit("not json at all")
        advanceTimeBy(1.seconds)
        advanceUntilIdle()

        assertEquals(before, remote.calls, "a bad frame must not cause a pull")
        assertEquals(1, channel.opens, "a bad frame must not drop the connection")
        assertFalse(channel.current.closed)
    }

    /** A burst is one pull, because the coordinator coalesces — see SyncPullCoordinator. */
    @Test
    fun aBurstOfInvalidatesCostsOnePull() = runTest {
        connection(backgroundScope).start()
        settleConnect(this)
        val before = remote.calls

        repeat(5) { channel.current.emit("""{"type":"invalidate","cursor":$it}""") }
        advanceTimeBy(1.seconds)
        advanceUntilIdle()

        // Not necessarily exactly one — a request landing after a pull has begun earns the next
        // one — but nothing like five.
        assertTrue(remote.calls - before <= 2, "burst cost ${remote.calls - before} pulls")
    }

    // --- lifecycle -----------------------------------------------------------------------------

    @Test
    fun startIsIdempotent() = runTest {
        val connection = connection(backgroundScope)
        connection.start()
        connection.start()
        settleConnect(this)

        assertEquals(1, channel.opens, "a second start() must not open a second socket")
    }

    @Test
    fun stopClosesAndStaysClosed() = runTest {
        val connection = connection(backgroundScope)
        connection.start()
        settleConnect(this)

        connection.stop()
        advanceTimeBy(10.seconds)
        advanceUntilIdle()

        assertEquals(1, channel.opens, "stop() must not leave the retry loop running")
        assertEquals(RealtimeConnectionState.Idle, connectivity.state.value)
    }
}

private fun Int.minutesAsSeconds() = (this * 60).seconds
