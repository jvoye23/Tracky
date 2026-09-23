package com.jvcs.tracky.core.domain.realtime

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * One socket, opened on demand.
 *
 * This interface is the test seam, and it exists because Ktor's `MockEngine` cannot fake a
 * WebSocket: it advertises the capability, but every `respond` overload builds a response whose
 * body is a byte channel, while the client plugin needs the engine to hand back a session. Faking
 * it there would mean implementing Ktor internals. So the Ktor adapter below this line is kept
 * free of decisions, verified by hand against the deployed backend, and everything that can be
 * got wrong lives above it where a fake can drive it.
 */
interface RealtimeChannel {

    /**
     * Opens one connection.
     *
     * [DataError.Remote.UNAUTHORIZED] is load-bearing: the client's `Auth` plugin refreshes on a
     * 401 *response*, and an upgrade rejected before a response never reaches it, so the caller
     * has to drive the refresh itself.
     */
    suspend fun open(): Result<RealtimeSession, DataError.Remote>
}

/** A connection that is up. */
interface RealtimeSession {

    /**
     * Text frames, in order. Completes normally when the server closes, which the caller reads as
     * "reconnect"; throws if the transport fails underneath it.
     */
    val incoming: Flow<String>

    suspend fun send(text: String)

    suspend fun close()
}
