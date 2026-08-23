package com.jvcs.tracky.core.domain.util

sealed interface DataError: Error {

    enum class Remote: DataError {
        REQUEST_TIMEOUT,
        UNAUTHORIZED,
        FORBIDDEN,
        CONFLICT,
        TOO_MANY_REQUESTS,
        NO_INTERNET,
        PAYLOAD_TOO_LARGE,
        SERVER_ERROR,
        SERVICE_UNAVAILABLE,
        SERIALIZATION,
        BAD_REQUEST,
        NOT_FOUND,
        UNKNOWN
    }

    enum class Local: DataError {
        DISK_FULL,
        NOT_FOUND,
        UNKNOWN
    }
}

fun DataError.Remote.isTransient(): Boolean = when (this) {
    DataError.Remote.NO_INTERNET,
    DataError.Remote.REQUEST_TIMEOUT,
    DataError.Remote.SERVER_ERROR,
    DataError.Remote.SERVICE_UNAVAILABLE,
    DataError.Remote.TOO_MANY_REQUESTS,
    DataError.Remote.UNKNOWN -> true
    else -> false
}

/**
 * True when the server is saying "that row is not there for you" — it never existed, it was already
 * deleted, or it belongs to someone else.
 *
 * The API deliberately collapses all three into `403`, so a response never confirms that a stranger's
 * id exists. The single exception is creating a subtask interval, where the `404` branch is only
 * reachable for a task interval the caller already owns and so leaks nothing
 * (Requirements/api/backend_documentation.md).
 *
 * Sync cannot tell the three apart and does not need to: the local row is authoritative for what
 * happens next either way. A delete is already done, and a write whose parent is missing gets one
 * more attempt through the queue before the drain gives up on it.
 */
fun DataError.Remote.isMissingOrForbidden(): Boolean =
    this == DataError.Remote.NOT_FOUND || this == DataError.Remote.FORBIDDEN
