package com.jvcs.tracky.core.data.realtime

import com.jvcs.tracky.core.data.networking.httpStatusToRemoteError
import com.jvcs.tracky.core.data.networking.toRemoteDataError
import com.jvcs.tracky.core.domain.realtime.RealtimeChannel
import com.jvcs.tracky.core.domain.realtime.RealtimeSession
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/**
 * The Ktor half of [RealtimeChannel], deliberately without a decision in it — see that interface
 * for why the seam is here rather than at the engine.
 *
 * The bearer token is not set by hand: `sendWithoutRequest` in `HttpClientFactory` attaches it to
 * every URL outside the auth routes, and a websocket upgrade goes through the ordinary request
 * pipeline, so the header is already there. One source of truth for the token, and the plugin's
 * refresh cache stays honest.
 */
class KtorRealtimeChannel(
    private val httpClient: HttpClient,
    /** Null when no base URL was configured; the connection then never tries. */
    private val url: String?
) : RealtimeChannel {

    override suspend fun open(): Result<RealtimeSession, DataError.Remote> {
        val target = url ?: return Result.Error(DataError.Remote.NOT_FOUND)
        return try {
            Result.Success(KtorRealtimeSession(httpClient.webSocketSession { url(target) }))
        } catch (e: CancellationException) {
            // Before the broad catch, as everywhere else in this layer: a cancelled connection is
            // the gate closing, not a failure to back off from.
            throw e
        } catch (e: ResponseException) {
            // A refused upgrade — 401 above all, which the caller turns into a token refresh.
            Result.Error(httpStatusToRemoteError(e.response.status.value))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.Error(e.toRemoteDataError())
        }
    }
}

private class KtorRealtimeSession(
    private val session: WebSocketSession
) : RealtimeSession {

    override val incoming: Flow<String> =
        session.incoming.consumeAsFlow()
            // Ping, pong and close are the engine's business; only text carries envelopes.
            .filterIsInstance<Frame.Text>()
            .map { it.readText() }

    override suspend fun send(text: String) {
        session.send(Frame.Text(text))
    }

    override suspend fun close() {
        try {
            session.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Closing a socket that is already gone is the normal case on a dropped connection.
            e.printStackTrace()
        }
    }
}
