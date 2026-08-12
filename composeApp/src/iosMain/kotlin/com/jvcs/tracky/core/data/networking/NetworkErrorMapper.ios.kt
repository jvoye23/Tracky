package com.jvcs.tracky.core.data.networking

import com.jvcs.tracky.core.domain.util.DataError
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDataNotAllowed
import platform.Foundation.NSURLErrorInternationalRoamingOff
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut

actual fun Throwable.toRemoteDataError(): DataError.Remote {
    // The Darwin engine wraps every NSURLSession failure in this, carrying the original NSError.
    val code = (this as? DarwinHttpRequestException)?.origin?.code
        ?: return DataError.Remote.UNKNOWN

    return when (code) {
        NSURLErrorNotConnectedToInternet,
        NSURLErrorNetworkConnectionLost,
        NSURLErrorCannotFindHost,
        NSURLErrorCannotConnectToHost,
        NSURLErrorDNSLookupFailed,
        NSURLErrorDataNotAllowed,
        NSURLErrorInternationalRoamingOff -> DataError.Remote.NO_INTERNET

        NSURLErrorTimedOut -> DataError.Remote.REQUEST_TIMEOUT

        else -> DataError.Remote.UNKNOWN
    }
}
