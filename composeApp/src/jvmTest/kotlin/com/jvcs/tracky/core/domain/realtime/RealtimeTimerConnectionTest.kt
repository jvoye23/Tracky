@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jvcs.tracky.core.domain.realtime

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
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
            pullCoordinator =
                SyncPullCoordinator(
                    deltaSyncApplier = testDeltaSyncApplier(remote = remote, cursorStore = cursorStore),
                    applicationScope = scope,
                    coalesceWindow = 10.milliseconds,
                ),
            connectivity = connectivity,
            json = Json { ignoreUnknownKeys = true },
            isOnline = online,
            isInForeground = foreground,
            isAuthenticated = authenticated,
            applicationScope = scope,
            random = random,
        )

    /** Past the 1s connectivity debounce, then let the handshake settle. */
    private fun settleConnect(scope: kotlinx.coroutines.test.TestScope) {
        scope.advanceTimeBy(2.seconds)
        scope.advanceUntilIdle()
    }

    // --- the gate ----------------------------------------------------------------------------

    @Test
    fun connectsWhenOnlineForegroundedAndSignedIn() =
        runTest {
            connection(backgroundScope).start()
            settleConnect(this)

            assertThat(channel.opens).isEqualTo(1)
            assertThat(connectivity.state.value).isEqualTo(RealtimeConnectionState.Connected)
        }

    @Test
    fun doesNotConnectWhileBackgrounded() =
        runTest {
            foreground.value = false
            connection(backgroundScope).start()
            settleConnect(this)

            assertThat(channel.opens).isEqualTo(0)
        }

    @Test
    fun doesNotConnectWhileSignedOut() =
        runTest {
            authenticated.value = false
            connection(backgroundScope).start()
            settleConnect(this)

            assertThat(channel.opens).isEqualTo(0)
        }

    @Test
    fun closesTheSocketWhenTheGateShuts() =
        runTest {
            connection(backgroundScope).start()
            settleConnect(this)
            val session = channel.current

            foreground.value = false
            // Past the connectivity debounce, which keeps the gate's combine busy for a moment.
            advanceTimeBy(3.seconds)
            advanceUntilIdle()

            assertThat(session.closed, name = "backgrounding must tear the socket down, not leave it open").isTrue()
        }

    // --- the handshake -----------------------------------------------------------------------

    @Test
    fun sendsExactlyOneHelloCarryingTheDeviceAndCursor() =
        runTest {
            cursorStore.setCursor(84213)
            connection(backgroundScope).start()
            settleConnect(this)

            assertThat(channel.current.sent.size).isEqualTo(1)
            val hello = channel.current.sent.single()
            assertThat(hello.contains(""""type":"hello""""), name = hello).isTrue()
            assertThat(hello.contains(""""cursor":84213"""), name = hello).isTrue()
        }

    /** Zero rather than absent, so the server's "are you behind" comparison is true. */
    @Test
    fun aDeviceThatHasNeverPulledAnnouncesCursorZero() =
        runTest {
            connection(backgroundScope).start()
            settleConnect(this)

            assertThat(
                channel.current.sent
                    .single()
                    .contains(""""cursor":0"""),
            ).isTrue()
        }

    // --- envelopes become pulls --------------------------------------------------------------

    @Test
    fun anInvalidateTriggersAPull() =
        runTest {
            connection(backgroundScope).start()
            settleConnect(this)
            val before = remote.calls

            channel.current.emit("""{"type":"invalidate","cursor":9}""")
            advanceTimeBy(1.seconds)
            advanceUntilIdle()

            assertThat(remote.calls).isEqualTo(before + 1)
        }

    @Test
    fun aTimerEnvelopeTriggersAPullToo() =
        runTest {
            connection(backgroundScope).start()
            settleConnect(this)
            val before = remote.calls

            channel.current.emit("""{"type":"timer","cursor":9,"active":null}""")
            advanceTimeBy(1.seconds)
            advanceUntilIdle()

            assertThat(remote.calls).isEqualTo(before + 1)
        }

    /** A future backend addition must not be able to break an old client. */
    @Test
    fun anUnknownEnvelopeTypeStillTriggersAPullAndKeepsTheSocket() =
        runTest {
            connection(backgroundScope).start()
            settleConnect(this)
            val before = remote.calls

            channel.current.emit("""{"type":"somethingNewInV2"}""")
            advanceTimeBy(1.seconds)
            advanceUntilIdle()

            assertThat(remote.calls).isEqualTo(before + 1)
            assertThat(channel.opens, name = "an unknown type must not drop the connection").isEqualTo(1)
        }

    @Test
    fun anUnreadableFrameIsIgnoredAndTheSocketSurvives() =
        runTest {
            connection(backgroundScope).start()
            settleConnect(this)
            val before = remote.calls

            channel.current.emit("not json at all")
            advanceTimeBy(1.seconds)
            advanceUntilIdle()

            assertThat(remote.calls, name = "a bad frame must not cause a pull").isEqualTo(before)
            assertThat(channel.opens, name = "a bad frame must not drop the connection").isEqualTo(1)
            assertThat(channel.current.closed).isFalse()
        }

    /** A burst is one pull, because the coordinator coalesces — see SyncPullCoordinator. */
    @Test
    fun aBurstOfInvalidatesCostsOnePull() =
        runTest {
            connection(backgroundScope).start()
            settleConnect(this)
            val before = remote.calls

            repeat(5) { channel.current.emit("""{"type":"invalidate","cursor":$it}""") }
            advanceTimeBy(1.seconds)
            advanceUntilIdle()

            // Not necessarily exactly one — a request landing after a pull has begun earns the next
            // one — but nothing like five.
            assertThat(remote.calls - before <= 2, name = "burst cost ${remote.calls - before} pulls").isTrue()
        }

    // --- reconnection ------------------------------------------------------------------------

    @Test
    fun reconnectsWhenTheServerHangsUp() =
        runTest {
            connection(backgroundScope).start()
            settleConnect(this)

            channel.current.closeFromServer()
            advanceTimeBy(5.seconds)
            advanceUntilIdle()

            assertThat(channel.opens >= 2, name = "expected a reconnect, saw ${channel.opens} opens").isTrue()
        }

    /** Backoff must grow, or a server that is down gets hammered. */
    @Test
    fun backoffGrowsBetweenFailedAttempts() =
        runTest {
            channel.failNextWith(
                DataError.Remote.SERVER_ERROR,
                DataError.Remote.SERVER_ERROR,
                DataError.Remote.SERVER_ERROR,
                DataError.Remote.SERVER_ERROR,
                DataError.Remote.SERVER_ERROR,
                DataError.Remote.SERVER_ERROR,
            )
            connection(backgroundScope).start()
            advanceTimeBy(2.seconds)
            advanceUntilIdle()
            val earlyOpens = channel.opens

            advanceTimeBy(2.seconds)
            advanceUntilIdle()
            val laterOpens = channel.opens - earlyOpens

            // The same two seconds buys fewer attempts later than it did at the start.
            assertThat(
                laterOpens < earlyOpens,
                name = "backoff did not grow: $earlyOpens attempts in the first window, $laterOpens in the second",
            ).isTrue()
        }

    @Test
    fun backoffIsCappedSoADeadServerIsStillRetried() =
        runTest {
            repeat(40) { channel.failNextWith(DataError.Remote.SERVER_ERROR) }
            connection(backgroundScope).start()
            advanceTimeBy(10.minutesAsSeconds())
            advanceUntilIdle()
            val opensByTenMinutes = channel.opens

            advanceTimeBy(2.minutesAsSeconds())
            advanceUntilIdle()

            assertThat(
                channel.opens > opensByTenMinutes,
                name = "a capped backoff must keep retrying; stalled at $opensByTenMinutes",
            ).isTrue()
        }

    // --- auth ---------------------------------------------------------------------------------

    /**
     * Ktor's Auth plugin refreshes on a 401 *response*; a refused upgrade never reaches it. The
     * connection drives the refresh by making the pull it owes anyway.
     */
    @Test
    fun aRefusedUpgradeDrivesARefreshByPulling() =
        runTest {
            channel.failNextWith(DataError.Remote.UNAUTHORIZED)
            connection(backgroundScope).start()
            advanceTimeBy(6.seconds)
            advanceUntilIdle()

            assertThat(remote.calls >= 1, name = "a 401 upgrade should have driven a REST pull to refresh").isTrue()
            assertThat(channel.opens >= 2, name = "and then retried the socket").isTrue()
        }

    @Test
    fun aPersistent401DegradesIntoOrdinaryBackoffRatherThanARefreshStorm() =
        runTest {
            repeat(20) { channel.failNextWith(DataError.Remote.UNAUTHORIZED) }
            connection(backgroundScope).start()
            advanceTimeBy(30.seconds)
            advanceUntilIdle()

            // Capped at MAX_AUTH_RETRIES; everything after is plain backoff, so pulls stay bounded
            // even though attempts continue.
            assertThat(remote.calls <= 3, name = "refresh storm: ${remote.calls} pulls").isTrue()
        }

    // --- lifecycle -----------------------------------------------------------------------------

    @Test
    fun startIsIdempotent() =
        runTest {
            val connection = connection(backgroundScope)
            connection.start()
            connection.start()
            settleConnect(this)

            assertThat(channel.opens, name = "a second start() must not open a second socket").isEqualTo(1)
        }

    @Test
    fun stopClosesAndStaysClosed() =
        runTest {
            val connection = connection(backgroundScope)
            connection.start()
            settleConnect(this)

            connection.stop()
            advanceTimeBy(10.seconds)
            advanceUntilIdle()

            assertThat(channel.opens, name = "stop() must not leave the retry loop running").isEqualTo(1)
            assertThat(connectivity.state.value).isEqualTo(RealtimeConnectionState.Idle)
        }
}

private fun Int.minutesAsSeconds() = (this * 60).seconds
