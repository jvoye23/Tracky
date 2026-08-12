package com.jvcs.tracky.core.data.networking

import com.jvcs.tracky.core.domain.util.DataError
import java.net.SocketException
import java.net.UnknownHostException

actual fun Throwable.toRemoteDataError(): DataError.Remote = when (this) {
    // OkHttp's offline signal: DNS resolution failed. Ktor's UnresolvedAddressException is a
    // typealias for java.nio.channels.UnresolvedAddressException, which OkHttp never throws.
    is UnknownHostException -> DataError.Remote.NO_INTERNET
    // Covers ConnectException, NoRouteToHostException, PortUnreachableException and
    // "connection reset" / "network unreachable". Safe as a catch-all only because safeCall
    // already peeled off ConnectTimeoutException, which subclasses ConnectException on JVM.
    is SocketException -> DataError.Remote.NO_INTERNET
    else -> DataError.Remote.UNKNOWN
}
