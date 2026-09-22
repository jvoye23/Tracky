package com.jvcs.tracky.core.data.networking

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.path

internal const val REALTIME_PATH = "/api/realtime"

/**
 * The socket's address, derived from the REST base.
 *
 * Takes the base as a parameter rather than reading [ApiConfig] itself, for two reasons. It is
 * generated at build time from `local.properties` and is **empty** on a fresh clone or in CI, and a
 * pure function can be tested without it. Null means "realtime is not available here", which the
 * connection treats as a reason to stay idle rather than to retry against a garbage URL forever.
 *
 * [constructRoute] deliberately is not reused: it would prefix the https base onto a ws path.
 */
internal fun realtimeUrl(baseUrl: String, path: String = REALTIME_PATH): String? {
    if (baseUrl.isBlank()) return null
    return try {
        URLBuilder(baseUrl).apply {
            protocol = when (protocol) {
                URLProtocol.HTTPS -> URLProtocol.WSS
                URLProtocol.HTTP -> URLProtocol.WS
                // Anything else is a misconfiguration, not something to guess at.
                else -> return null
            }
            path(path)
        }.buildString()
    } catch (e: IllegalArgumentException) {
        null
    }
}
