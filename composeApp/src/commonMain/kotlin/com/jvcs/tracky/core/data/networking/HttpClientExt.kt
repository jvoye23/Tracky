package com.jvcs.tracky.core.data.networking

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

fun constructRoute(route: String): String {
    return when {
        route.contains(ApiConfig.BASE_URL) -> route
        route.startsWith("/") -> ApiConfig.BASE_URL + route
        route.startsWith("https") -> route
        else -> ApiConfig.BASE_URL + "/$route"
    }
}

suspend inline fun <reified T> responseToResult(response: HttpResponse): Result<T, DataError.Remote> {
    return when (response.status.value) {
        in 200..299 -> {
            try {
                Result.Success(response.body<T>())
            } catch (e: Exception) {
                Result.Error(DataError.Remote.SERIALIZATION)
            }
        }
        400 -> Result.Error(DataError.Remote.BAD_REQUEST)
        401 -> Result.Error(DataError.Remote.UNAUTHORIZED)
        403 -> Result.Error(DataError.Remote.FORBIDDEN)
        404 -> Result.Error(DataError.Remote.NOT_FOUND)
        408 -> Result.Error(DataError.Remote.REQUEST_TIMEOUT)
        409 -> Result.Error(DataError.Remote.CONFLICT)
        413 -> Result.Error(DataError.Remote.PAYLOAD_TOO_LARGE)
        429 -> Result.Error(DataError.Remote.TOO_MANY_REQUESTS)
        503 -> Result.Error(DataError.Remote.SERVICE_UNAVAILABLE)
        in 500..599 -> Result.Error(DataError.Remote.SERVER_ERROR)
        else -> Result.Error(DataError.Remote.UNKNOWN)
    }
}

suspend inline fun <reified Response : Any> safeCall(
    execute: () -> HttpResponse
): Result<Response, DataError.Remote> {
    // Order is load-bearing: on JVM, Ktor's ConnectTimeoutException subclasses
    // java.net.ConnectException, so the timeout branches must precede anything that treats a
    // connect/socket failure as "offline" — otherwise timeouts get reported as NO_INTERNET.
    val response = try {
        execute()
    } catch (e: UnresolvedAddressException) {
        // CIO / native DNS failure. OkHttp signals this as UnknownHostException instead,
        // which toRemoteDataError() picks up below.
        e.printStackTrace()
        return Result.Error(DataError.Remote.NO_INTERNET)
    } catch (e: ConnectTimeoutException) {
        e.printStackTrace()
        return Result.Error(DataError.Remote.REQUEST_TIMEOUT)
    } catch (e: SocketTimeoutException) {
        e.printStackTrace()
        return Result.Error(DataError.Remote.REQUEST_TIMEOUT)
    } catch (e: HttpRequestTimeoutException) {
        e.printStackTrace()
        return Result.Error(DataError.Remote.REQUEST_TIMEOUT)
    } catch (e: SerializationException) {
        e.printStackTrace()
        return Result.Error(DataError.Remote.SERIALIZATION)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        e.printStackTrace()
        return Result.Error(e.toRemoteDataError())
    }
    return responseToResult(response)
}

suspend inline fun <reified Request, reified Response : Any> HttpClient.post(
    route: String,
    body: Request,
    queryParams: Map<String, Any> = mapOf(),
    crossinline builder: HttpRequestBuilder.() -> Unit = {}
): Result<Response, DataError.Remote> {
    return safeCall {
        post {
            url(constructRoute(route))
            queryParams.forEach { (key, value) -> parameter(key, value) }
            setBody(body)
            builder()
        }
    }
}

suspend inline fun <reified Response : Any> HttpClient.get(
    route: String,
    queryParams: Map<String, Any> = mapOf(),
    crossinline builder: HttpRequestBuilder.() -> Unit = {}
): Result<Response, DataError.Remote> {
    return safeCall {
        get {
            url(constructRoute(route))
            queryParams.forEach { (key, value) -> parameter(key, value) }
            builder()
        }
    }
}

suspend inline fun <reified Request, reified Response: Any> HttpClient.put(
    route: String,
    body: Request,
    contentType: ContentType = ContentType.Application.Json
): Result<Response, DataError.Remote> {
    return safeCall {
        put {
            url(constructRoute(route))
            setBody(body)
            contentType(
                contentType
            )
        }
    }
}

suspend inline fun <reified Response: Any> HttpClient.delete(
    route: String,
    queryParameters: Map<String, Any?> = mapOf()
): Result<Response, DataError.Remote> {
    return safeCall {
        delete {
            url(constructRoute(route))
            queryParameters.forEach { (key, value) ->
                parameter(key, value)
            }
        }
    }
}
