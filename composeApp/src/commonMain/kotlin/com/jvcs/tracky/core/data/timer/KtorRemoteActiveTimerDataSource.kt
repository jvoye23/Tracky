package com.jvcs.tracky.core.data.timer

import com.jvcs.tracky.core.data.networking.constructRoute
import com.jvcs.tracky.core.data.networking.dto.ActiveTimerChangeDto
import com.jvcs.tracky.core.data.networking.dto.ActiveTimerConflictDto
import com.jvcs.tracky.core.data.networking.dto.ActiveTimerDto
import com.jvcs.tracky.core.data.networking.dto.StopActiveTimerRequest
import com.jvcs.tracky.core.data.networking.httpStatusToRemoteError
import com.jvcs.tracky.core.data.networking.mappers.toActiveTimer
import com.jvcs.tracky.core.data.networking.mappers.toActiveTimerChange
import com.jvcs.tracky.core.data.networking.mappers.toRejected
import com.jvcs.tracky.core.data.networking.mappers.toRequest
import com.jvcs.tracky.core.data.networking.safeResponse
import com.jvcs.tracky.core.domain.timer.ActiveTimer
import com.jvcs.tracky.core.domain.timer.ActiveTimerChange
import com.jvcs.tracky.core.domain.timer.RemoteActiveTimerDataSource
import com.jvcs.tracky.core.domain.timer.StartActiveTimer
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.time.Instant

/**
 * Talks to `/api/timer/active`.
 *
 * Deliberately does not use the shared `get`/`put`/`post` helpers, because two of this resource's
 * statuses carry meaning those helpers throw away: `204` means "nothing is running", which is an
 * answer rather than an empty body, and `409` carries the timer that *is* running.
 */
class KtorRemoteActiveTimerDataSource(private val httpClient: HttpClient) : RemoteActiveTimerDataSource {

    override suspend fun getActive(): Result<ActiveTimer?, DataError.Remote> {
        val response =
            when (val r = safeResponse { httpClient.get { url(constructRoute(ACTIVE_ROUTE)) } }) {
                is Result.Success -> r.data
                is Result.Error -> return Result.Error(r.error)
            }
        return when {
            response.status.value == NO_CONTENT -> {
                Result.Success(null)
            }

            response.status.value in SUCCESS -> {
                response.decode<ActiveTimerDto>()?.let {
                    Result.Success(it.toActiveTimer())
                } ?: Result.Error(DataError.Remote.SERIALIZATION)
            }

            else -> {
                Result.Error(httpStatusToRemoteError(response.status.value))
            }
        }
    }

    override suspend fun start(request: StartActiveTimer): Result<ActiveTimerChange, DataError.Remote> =
        changing {
            httpClient.put {
                url(constructRoute(ACTIVE_ROUTE))
                contentType(ContentType.Application.Json)
                setBody(request.toRequest())
            }
        }

    override suspend fun stop(intervalId: String, endedAt: Instant): Result<ActiveTimerChange, DataError.Remote> =
        changing {
            httpClient.post {
                url(constructRoute(STOP_ROUTE))
                contentType(ContentType.Application.Json)
                setBody(StopActiveTimerRequest(intervalId = intervalId, endedAtUtc = endedAt.toString()))
            }
        }

    /**
     * The shared success/conflict handling for start and stop, which answer identically.
     *
     * A `409` whose body will not decode still has to come back as a refusal rather than a
     * transport error: retrying it would repeat a request the server has already ruled on.
     */
    private suspend inline fun changing(
        crossinline execute: suspend () -> HttpResponse,
    ): Result<ActiveTimerChange, DataError.Remote> {
        val response =
            when (val r = safeResponse { execute() }) {
                is Result.Success -> r.data
                is Result.Error -> return Result.Error(r.error)
            }
        return when {
            response.status.value in SUCCESS -> {
                response.decode<ActiveTimerChangeDto>()?.let {
                    Result.Success(it.toActiveTimerChange())
                } ?: Result.Error(DataError.Remote.SERIALIZATION)
            }

            response.status.value == CONFLICT -> {
                Result.Success(
                    response.decode<ActiveTimerConflictDto>()?.toRejected()
                        ?: ActiveTimerChange.Rejected(active = null, serverNow = null),
                )
            }

            else -> {
                Result.Error(httpStatusToRemoteError(response.status.value))
            }
        }
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T? = runCatching { body<T>() }.getOrNull()

    private companion object {
        const val ACTIVE_ROUTE = "/api/timer/active"
        const val STOP_ROUTE = "/api/timer/active/stop"
        const val NO_CONTENT = 204
        const val CONFLICT = 409
        val SUCCESS = 200..299
    }
}
