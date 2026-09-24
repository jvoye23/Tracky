@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package com.jvcs.tracky.core.domain.realtime

import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.data.networking.dto.HelloEnvelopeDto
import com.jvcs.tracky.core.data.networking.dto.RealtimeEnvelopeParser
import com.jvcs.tracky.core.domain.device.DeviceIdProvider
import com.jvcs.tracky.core.domain.sync.SyncCursorStore
import com.jvcs.tracky.core.domain.sync.SyncPullCoordinator
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps a socket open while the app is in front of the user, and turns anything the server says
 * into a pull.
 *
 * **The socket is a nudge, never a carrier.** Every envelope means at most "there may be something
 * newer than your cursor", and the delta feed remains the only way rows arrive. That is the
 * backend spec's rule and it is what keeps the poll a genuine fallback rather than decoration: a
 * device that misses every message still catches up on its next tick.
 *
 * Gated on connectivity *and* foreground *and* a session, so a backgrounded, offline or logged-out
 * app holds no socket. `flatMapLatest` does the tearing down — when the gate closes the whole
 * retry loop is cancelled, not merely told to stop.
 */
class RealtimeTimerConnection(
    private val channel: RealtimeChannel,
    private val parser: RealtimeEnvelopeParser,
    private val deviceIdProvider: DeviceIdProvider,
    private val syncCursorStore: SyncCursorStore,
    private val pullCoordinator: SyncPullCoordinator,
    private val connectivity: RealtimeConnectivity,
    private val json: Json,
    /**
     * Flows rather than the `expect class` observers on purpose: their JVM actuals are hard-wired
     * `flowOf(true)`, so taking the classes would make every gate transition untestable.
     */
    private val isOnline: Flow<Boolean>,
    private val isInForeground: Flow<Boolean>,
    private val isAuthenticated: Flow<Boolean>,
    private val applicationScope: CoroutineScope,
    private val random: Random = Random.Default,
) {

    private var started = false
    private var job: Job? = null

    fun start() {
        if (started) return
        started = true
        job =
            combine(
                isOnline.debounce(CONNECTIVITY_DEBOUNCE),
                isInForeground,
                isAuthenticated,
            ) { online, foreground, authed -> online && foreground && authed }
                .distinctUntilChanged()
                .flatMapLatest { active ->
                    if (active) {
                        connectionLoop()
                    } else {
                        // The gate owns Idle. The loop never reports its own teardown, because a
                        // cancelled coroutine's finally would race this and win.
                        connectivity.set(RealtimeConnectionState.Idle)
                        emptyFlow()
                    }
                }.launchIn(applicationScope)
    }

    /** For teardown. The [isAuthenticated] gate already covers an ordinary logout. */
    fun stop() {
        job?.cancel()
        job = null
        started = false
        connectivity.set(RealtimeConnectionState.Idle)
    }

    private fun connectionLoop(): Flow<Nothing> =
        flow {
            var attempt = 0
            var authRetries = 0

            while (true) {
                connectivity.set(RealtimeConnectionState.Connecting)

                when (val opened = channel.open()) {
                    is Result.Error -> {
                        connectivity.set(RealtimeConnectionState.Disconnected)
                        // Ktor's Auth plugin refreshes on a 401 *response*; an upgrade refused before
                        // one never reaches it. Rather than run a second refresh here — two of them
                        // racing over a rotating refresh token is a way to log the user out — drive
                        // the plugin's own by making the pull we owe the socket anyway. If that
                        // refresh fails the session is cleared, isAuthenticated goes false, and the
                        // gate cancels this loop: no hammering, and no special case for it.
                        val isAuthFailure =
                            opened.error == DataError.Remote.UNAUTHORIZED ||
                                opened.error == DataError.Remote.FORBIDDEN
                        if (isAuthFailure && authRetries < MAX_AUTH_RETRIES) {
                            authRetries++
                            pullCoordinator.pullNow()
                            delay(AUTH_RETRY_DELAY)
                        } else {
                            delay(backoff(++attempt))
                        }
                    }

                    is Result.Success -> {
                        val session = opened.data
                        try {
                            connectivity.set(RealtimeConnectionState.Connected)
                            session.send(
                                json.encodeToString(
                                    HelloEnvelopeDto(
                                        deviceId = deviceIdProvider.deviceId(),
                                        // Zero, not absent: it makes the server's "is this client
                                        // behind" comparison true, so a device that has never pulled
                                        // gets its catch-up nudge straight away.
                                        cursor = syncCursorStore.cursor() ?: 0L,
                                    ),
                                ),
                            )
                            session.incoming.collect { frame ->
                                onEvent(frame) {
                                    attempt = 0
                                    authRetries = 0
                                }
                            }
                        } catch (exception: IOException) {
                            // A socket that died under us is the ordinary case, not an error worth
                            // surfacing. Reconnect.
                            Logger.withTag("RealtimeTimerConnection").w(exception) { "connectionLoop: IOException" }
                        } finally {
                            // The scope may already be cancelled — the gate closing is exactly that —
                            // and the close frame is still worth sending.
                            withContext(NonCancellable) { session.close() }
                        }
                        // Only past the finally, so this never runs on a cancelled teardown: the state
                        // there is Idle, set by whoever closed the gate, and a Disconnected written
                        // afterwards would be a lie that outlives the connection.
                        connectivity.set(RealtimeConnectionState.Disconnected)
                        delay(backoff(++attempt))
                    }
                }
            }
        }

    private suspend fun onEvent(frame: String, onHandshake: () -> Unit) {
        when (parser.parse(frame)) {
            // Unreadable. Logged by the parser; dropping a healthy connection over one bad frame
            // would be a far worse answer than ignoring it.
            null -> {
                return
            }

            RealtimeEvent.Ready -> {
                // Reset here rather than on connect: a socket that opens and is immediately closed
                // has not proved anything, and must keep escalating its backoff.
                onHandshake()
                // The server follows `ready` with an `invalidate` only if it thinks we are behind.
                // Pulling anyway costs one empty page per connection — and connections are rare —
                // and closes the gap without trusting that comparison to be exactly right.
                pullCoordinator.request()
            }

            // Invalidate, Timer, and anything a later backend adds all mean the same thing.
            else -> {
                pullCoordinator.request()
            }
        }
    }

    /**
     * Exponential with equal jitter: half the window fixed, half random, so a fleet of devices
     * that all lost the server at once do not come back in lockstep.
     */
    private fun backoff(attempt: Int): Duration {
        val ceiling = MAX_BACKOFF.inWholeMilliseconds.toDouble()
        val exponential = (BASE_BACKOFF.inWholeMilliseconds * 2.0.pow(attempt - 1)).coerceAtMost(ceiling)
        val half = exponential / 2
        return (half + half * random.nextDouble()).toLong().milliseconds
    }

    private companion object {
        val CONNECTIVITY_DEBOUNCE = 1.seconds
        val BASE_BACKOFF = 1.seconds

        /**
         * No point backing off past the poll interval: beyond that the fallback is already the
         * faster of the two, and the socket is only costing us a reconnect attempt.
         */
        val MAX_BACKOFF = 30.seconds

        val AUTH_RETRY_DELAY = 1.seconds

        /** After this many, a 401 is treated as an ordinary failure rather than a stale token. */
        const val MAX_AUTH_RETRIES = 2
    }
}
