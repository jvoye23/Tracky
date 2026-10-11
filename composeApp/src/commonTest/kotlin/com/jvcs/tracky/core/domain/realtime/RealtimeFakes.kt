package com.jvcs.tracky.core.domain.realtime

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

/**
 * A socket a test drives by hand.
 *
 * Each [open] hands back a fresh session, so a test can drop one and assert the reconnect. Frames
 * are pushed in with [emit] and the session ends when [closeFromServer] is called, which is what a
 * server hanging up looks like from here.
 */
internal class FakeRealtimeChannel(private val failures: MutableList<DataError.Remote> = mutableListOf()) :
    RealtimeChannel {

    val sessions = mutableListOf<FakeRealtimeSession>()
    var opens = 0
        private set

    /** Queue an error for the next open; drained one per call. */
    fun failNextWith(vararg errors: DataError.Remote) = apply { failures += errors }

    override suspend fun open(): Result<RealtimeSession, DataError.Remote> {
        opens++
        if (failures.isNotEmpty()) return Result.Error(failures.removeAt(0))
        return Result.Success(FakeRealtimeSession().also { sessions += it })
    }

    val current: FakeRealtimeSession get() = sessions.last()
}

internal class FakeRealtimeSession : RealtimeSession {

    private val frames = Channel<String>(Channel.UNLIMITED)
    val sent = mutableListOf<String>()
    var closed = false
        private set

    override val incoming: Flow<String> = frames.consumeAsFlow()

    override suspend fun send(text: String) {
        sent += text
    }

    override suspend fun close() {
        closed = true
        frames.close()
    }

    /** A frame arriving from the server. */
    fun emit(frame: String) {
        frames.trySend(frame)
    }

    /** The server hanging up: the incoming flow completes and the connection reconnects. */
    fun closeFromServer() {
        frames.close()
    }
}
