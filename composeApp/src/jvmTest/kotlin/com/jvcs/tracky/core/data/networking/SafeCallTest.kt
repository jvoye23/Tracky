package com.jvcs.tracky.core.data.networking

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `safeCall`'s parameter is a plain `() -> HttpResponse`, so the transport failures each engine
 * throws can be reproduced by throwing directly — no HttpClient or MockEngine needed.
 */
class SafeCallTest {

    private fun classify(throwable: Throwable): Result<String, DataError.Remote> =
        runBlocking { safeCall { throw throwable } }

    @Test
    fun unknownHost_isReportedAsNoInternet() {
        // The regression: Android/JVM run on the OkHttp engine, which signals a DNS failure with
        // java.net.UnknownHostException. Ktor's UnresolvedAddressException is a typealias for
        // java.nio.channels.UnresolvedAddressException (a CIO thing), so this used to fall through
        // to the catch-all and surface as "an unknown error happened".
        assertEquals(
            Result.Error(DataError.Remote.NO_INTERNET),
            classify(UnknownHostException("api.example.com"))
        )
    }

    @Test
    fun connectionRefused_isReportedAsNoInternet() {
        assertEquals(
            Result.Error(DataError.Remote.NO_INTERNET),
            classify(ConnectException("Connection refused"))
        )
    }

    @Test
    fun networkUnreachable_isReportedAsNoInternet() {
        assertEquals(
            Result.Error(DataError.Remote.NO_INTERNET),
            classify(SocketException("Network is unreachable"))
        )
    }

    @Test
    fun unresolvedAddress_isReportedAsNoInternet() {
        // Still correct if the engine is ever swapped to CIO.
        assertEquals(
            Result.Error(DataError.Remote.NO_INTERNET),
            classify(UnresolvedAddressException())
        )
    }

    @Test
    fun socketTimeout_isReportedAsRequestTimeout() {
        assertEquals(
            Result.Error(DataError.Remote.REQUEST_TIMEOUT),
            classify(SocketTimeoutException("Read timed out"))
        )
    }

    @Test
    fun connectTimeout_isReportedAsRequestTimeout_notNoInternet() {
        // Guards the catch ordering: on JVM, Ktor's ConnectTimeoutException subclasses
        // java.net.ConnectException. If the timeout branches ever move below the connect-failure
        // handling, a timeout starts masquerading as "no internet" and this test fails.
        assertEquals(
            Result.Error(DataError.Remote.REQUEST_TIMEOUT),
            classify(ConnectTimeoutException("Connect timeout has expired"))
        )
    }

    @Test
    fun serializationFailure_isReportedAsSerialization() {
        assertEquals(
            Result.Error(DataError.Remote.SERIALIZATION),
            classify(SerializationException("Unexpected JSON token"))
        )
    }

    @Test
    fun unrecognisedFailure_staysUnknown() {
        assertEquals(
            Result.Error(DataError.Remote.UNKNOWN),
            classify(IllegalStateException("boom"))
        )
    }

    @Test
    fun cancellation_isRethrown_ratherThanTurnedIntoAnError() {
        // Swallowing this would break structured concurrency for every caller.
        assertFailsWith<CancellationException> { classify(CancellationException("cancelled")) }
    }
}
