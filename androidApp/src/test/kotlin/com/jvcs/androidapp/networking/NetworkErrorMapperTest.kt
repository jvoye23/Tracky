package com.jvcs.androidapp.networking

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.data.networking.safeCall
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.client.network.sockets.ConnectTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * How `safeCall` classifies the failures Android's OkHttp engine throws.
 *
 * These live here, not in composeApp's commonTest, because they are `java.net` exceptions and the
 * shared tests run on the iOS simulator. The engine-independent cases are in `SafeCallTest`.
 */
class NetworkErrorMapperTest {

    private fun classify(throwable: Throwable): Result<String, DataError.Remote> = runBlocking { safeCall { throw throwable } }

    @Test
    fun unknownHostIsReportedAsNoInternet() {
        // The regression: OkHttp signals a DNS failure with java.net.UnknownHostException. Ktor's
        // UnresolvedAddressException is a typealias for java.nio.channels.UnresolvedAddressException
        // (a CIO thing), so this used to fall through and surface as "an unknown error happened".
        assertThat(classify(UnknownHostException("api.example.com")))
            .isEqualTo(Result.Error(DataError.Remote.NO_INTERNET))
    }

    @Test
    fun connectionRefusedIsReportedAsNoInternet() {
        assertThat(classify(ConnectException("Connection refused")))
            .isEqualTo(Result.Error(DataError.Remote.NO_INTERNET))
    }

    @Test
    fun networkUnreachableIsReportedAsNoInternet() {
        assertThat(classify(SocketException("Network is unreachable")))
            .isEqualTo(Result.Error(DataError.Remote.NO_INTERNET))
    }

    @Test
    fun socketTimeoutIsReportedAsRequestTimeout() {
        assertThat(classify(SocketTimeoutException("Read timed out")))
            .isEqualTo(Result.Error(DataError.Remote.REQUEST_TIMEOUT))
    }

    @Test
    fun connectTimeoutIsReportedAsRequestTimeoutNotNoInternet() {
        // Guards the catch ordering: on the JVM, Ktor's ConnectTimeoutException subclasses
        // java.net.ConnectException. If the timeout branches ever move below the connect-failure
        // handling, a timeout starts masquerading as "no internet" and this test fails.
        assertThat(classify(ConnectTimeoutException("Connect timeout has expired")))
            .isEqualTo(Result.Error(DataError.Remote.REQUEST_TIMEOUT))
    }
}
