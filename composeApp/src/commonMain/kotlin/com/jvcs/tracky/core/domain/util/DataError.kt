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