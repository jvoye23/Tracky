package com.jvcs.tracky.core.data.networking

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
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
        assertThat(
            classify(UnknownHostException("api.example.com")),
        ).isEqualTo(Result.Error(DataError.Remote.NO_INTERNET))
    }

    @Test
    fun connectionRefused_isReportedAsNoInternet() {
        assertThat(
            classify(ConnectException("Connection refused")),
        ).isEqualTo(Result.Error(DataError.Remote.NO_INTERNET))
    }

    @Test
    fun networkUnreachable_isReportedAsNoInternet() {
        assertThat(
            classify(SocketException("Network is unreachable")),
        ).isEqualTo(Result.Error(DataError.Remote.NO_INTERNET))
    }

    @Test
    fun unresolvedAddress_isReportedAsNoInternet() {
        // Still correct if the engine is ever swapped to CIO.
        assertThat(classify(UnresolvedAddressException())).isEqualTo(Result.Error(DataError.Remote.NO_INTERNET))
    }

    @Test
    fun socketTimeout_isReportedAsRequestTimeout() {
        assertThat(
            classify(SocketTimeoutException("Read timed out")),
        ).isEqualTo(Result.Error(DataError.Remote.REQUEST_TIMEOUT))
    }

    @Test
    fun connectTimeout_isReportedAsRequestTimeout_notNoInternet() {
        // Guards the catch ordering: on JVM, Ktor's ConnectTimeoutException subclasses
        // java.net.ConnectException. If the timeout branches ever move below the connect-failure
        // handling, a timeout starts masquerading as "no internet" and this test fails.
        assertThat(
            classify(ConnectTimeoutException("Connect timeout has expired")),
        ).isEqualTo(Result.Error(DataError.Remote.REQUEST_TIMEOUT))
    }

    @Test
    fun serializationFailure_isReportedAsSerialization() {
        assertThat(
            classify(SerializationException("Unexpected JSON token")),
        ).isEqualTo(Result.Error(DataError.Remote.SERIALIZATION))
    }

    @Test
    fun unrecognisedIoFailure_staysUnknown() {
        assertThat(classify(java.io.IOException("stream reset"))).isEqualTo(Result.Error(DataError.Remote.UNKNOWN))
    }

    @Test
    fun nonTransportFailure_propagates_ratherThanBecomingAnError() {
        // Only transport failures are the network's to report. A programming error surfaces.
        assertFailure { classify(IllegalStateException("boom")) }.isInstanceOf<IllegalStateException>()
    }

    @Test
    fun cancellation_isRethrown_ratherThanTurnedIntoAnError() {
        // Swallowing this would break structured concurrency for every caller.
        assertFailure { classify(CancellationException("cancelled")) }.isInstanceOf<CancellationException>()
    }
}
