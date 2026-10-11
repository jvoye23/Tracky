package com.jvcs.tracky.core.data.networking

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test

/**
 * `safeCall`'s parameter is a plain `() -> HttpResponse`, so the transport failures each engine
 * throws can be reproduced by throwing directly — no HttpClient or MockEngine needed.
 *
 * These are the engine-independent cases. OkHttp's `java.net` failures, and the catch ordering
 * that only matters on the JVM, are covered by `androidApp`'s `NetworkErrorMapperTest`.
 */
class SafeCallTest {

    private fun classify(throwable: Throwable): Result<String, DataError.Remote> =
        runBlocking { safeCall { throw throwable } }

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
        assertThat(classify(IOException("stream reset"))).isEqualTo(Result.Error(DataError.Remote.UNKNOWN))
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
