package com.jvcs.tracky.core.data.networking

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

private const val FIRST_SERVER_ERROR = 500
private const val LAST_SERVER_ERROR = 599

fun constructRoute(route: String): String =
    when {
        route.contains(ApiConfig.BASE_URL) -> route
        route.startsWith("/") -> ApiConfig.BASE_URL + route
        route.startsWith("https") -> route
        else -> ApiConfig.BASE_URL + "/$route"
    }

suspend inline fun <reified T> responseToResult(response: HttpResponse): Result<T, DataError.Remote> =
    when {
        response.status.isSuccess() -> {
            try {
                Result.Success(response.body<T>())
            } catch (exception: Exception) {
                Result.Error(DataError.Remote.SERIALIZATION)
            }
        }

        else -> {
            Result.Error(httpStatusToRemoteError(response.status.value))
        }
    }

/**
 * The status-code half of [responseToResult], split out so a caller that needs the *body* of a
 * non-2xx response can reuse the mapping instead of restating it.
 *
 * `/api/timer/active` is the case: a `409` there is a domain answer carrying the timer that is
 * actually running, not an opaque failure.
 */
fun httpStatusToRemoteError(status: Int): DataError.Remote =
    when (status) {
        HttpStatusCode.BadRequest.value -> DataError.Remote.BAD_REQUEST
        HttpStatusCode.Unauthorized.value -> DataError.Remote.UNAUTHORIZED
        HttpStatusCode.Forbidden.value -> DataError.Remote.FORBIDDEN
        HttpStatusCode.NotFound.value -> DataError.Remote.NOT_FOUND
        HttpStatusCode.RequestTimeout.value -> DataError.Remote.REQUEST_TIMEOUT
        HttpStatusCode.Conflict.value -> DataError.Remote.CONFLICT
        HttpStatusCode.PayloadTooLarge.value -> DataError.Remote.PAYLOAD_TOO_LARGE
        HttpStatusCode.TooManyRequests.value -> DataError.Remote.TOO_MANY_REQUESTS
        HttpStatusCode.ServiceUnavailable.value -> DataError.Remote.SERVICE_UNAVAILABLE
        in FIRST_SERVER_ERROR..LAST_SERVER_ERROR -> DataError.Remote.SERVER_ERROR
        else -> DataError.Remote.UNKNOWN
    }

suspend inline fun <reified Response : Any> safeCall(execute: () -> HttpResponse): Result<Response, DataError.Remote> =
    when (val response = safeResponse(execute)) {
        is Result.Success -> responseToResult(response.data)
        is Result.Error -> response
    }

/**
 * [safeCall] without the status mapping: every transport failure is still turned into a
 * [DataError.Remote], but any response the server actually sent comes back intact, whatever its
 * status.
 *
 * For callers that have to read a non-2xx body, or tell `204 No Content` apart from a body that
 * failed to decode — [safeCall] reports both as an error.
 */
suspend inline fun safeResponse(execute: () -> HttpResponse): Result<HttpResponse, DataError.Remote> {
    // Order is load-bearing: on JVM, Ktor's ConnectTimeoutException subclasses
    // java.net.ConnectException, so the timeout branches must precede anything that treats a
    // connect/socket failure as "offline" — otherwise timeouts get reported as NO_INTERNET.
    val response =
        try {
            execute()
        } catch (exception: UnresolvedAddressException) {
            // CIO / native DNS failure. OkHttp signals this as UnknownHostException instead,
            // which toRemoteDataError() picks up below.
            exception.printStackTrace()
            return Result.Error(DataError.Remote.NO_INTERNET)
        } catch (exception: ConnectTimeoutException) {
            exception.printStackTrace()
            return Result.Error(DataError.Remote.REQUEST_TIMEOUT)
        } catch (exception: SocketTimeoutException) {
            exception.printStackTrace()
            return Result.Error(DataError.Remote.REQUEST_TIMEOUT)
        } catch (exception: HttpRequestTimeoutException) {
            exception.printStackTrace()
            return Result.Error(DataError.Remote.REQUEST_TIMEOUT)
        } catch (exception: SerializationException) {
            exception.printStackTrace()
            return Result.Error(DataError.Remote.SERIALIZATION)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            exception.printStackTrace()
            return Result.Error(exception.toRemoteDataError())
        }
    return Result.Success(response)
}

suspend inline fun <reified Request, reified Response : Any> HttpClient.post(
    route: String,
    body: Request,
    queryParams: Map<String, Any> = mapOf(),
    crossinline builder: HttpRequestBuilder.() -> Unit = {},
): Result<Response, DataError.Remote> =
    safeCall {
        post {
            url(constructRoute(route))
            queryParams.forEach { (key, value) -> parameter(key, value) }
            setBody(body)
            builder()
        }
    }

suspend inline fun <reified Response : Any> HttpClient.get(
    route: String,
    queryParams: Map<String, Any> = mapOf(),
    crossinline builder: HttpRequestBuilder.() -> Unit = {},
): Result<Response, DataError.Remote> =
    safeCall {
        get {
            url(constructRoute(route))
            queryParams.forEach { (key, value) -> parameter(key, value) }
            builder()
        }
    }

suspend inline fun <reified Request, reified Response : Any> HttpClient.put(
    route: String,
    body: Request,
    contentType: ContentType = ContentType.Application.Json,
): Result<Response, DataError.Remote> =
    safeCall {
        put {
            url(constructRoute(route))
            setBody(body)
            contentType(
                contentType,
            )
        }
    }

suspend inline fun <reified Response : Any> HttpClient.delete(
    route: String,
    queryParameters: Map<String, Any?> = mapOf(),
): Result<Response, DataError.Remote> =
    safeCall {
        delete {
            url(constructRoute(route))
            queryParameters.forEach { (key, value) ->
                parameter(key, value)
            }
        }
    }
