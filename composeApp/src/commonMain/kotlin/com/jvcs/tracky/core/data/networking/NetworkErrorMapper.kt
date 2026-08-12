package com.jvcs.tracky.core.data.networking

import com.jvcs.tracky.core.domain.util.DataError

/**
 * Maps an engine-specific transport exception to a [DataError.Remote].
 *
 * Ktor's *common* failures (unresolved address, connect/socket timeouts) are already handled in
 * [safeCall]; this only classifies what the platform engine throws directly — OkHttp's
 * `java.net.*` exceptions on Android/JVM, `DarwinHttpRequestException` on iOS.
 *
 * Returns [DataError.Remote.UNKNOWN] for anything unrecognised.
 */
expect fun Throwable.toRemoteDataError(): DataError.Remote
