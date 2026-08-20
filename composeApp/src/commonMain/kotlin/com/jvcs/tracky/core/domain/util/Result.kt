package com.jvcs.tracky.core.domain.util



sealed interface Result<out D, out E: Error> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Error<out E: com.jvcs.tracky.core.domain.util.Error>(val error: E) : Result<Nothing, E>

}

inline fun <T, E: com.jvcs.tracky.core.domain.util.Error, R> Result<T, E>.map(map: (T) -> R): Result<R, E> {
    return when (this) {
        is Result.Error -> Result.Error(error)
        is Result.Success -> Result.Success(map(data))
    }
}

inline fun <T, E: Error> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    return when (this) {
        is Result.Error -> this
        is Result.Success -> {
            action(data)
            this
        }
    }
}

inline fun <T, E: Error> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> {
    return when (this) {
        is Result.Error -> {
            action(error)
            this
        }
        is Result.Success -> this
    }
}

fun <T, E: Error> Result<T, E>.asEmptyDataResult(): EmptyResult<E> {
    return map {  }
}

typealias EmptyResult<E> = Result<Unit, E>
/** Unwraps a success, falling back to [default] on failure. For reads where "unknown" is survivable. */
fun <T, E: Error> Result<T, E>.getOrDefault(default: T): T {
    return when (this) {
        is Result.Success -> data
        is Result.Error -> default
    }
}
