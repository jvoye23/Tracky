package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.isTransient

/** What the drain should do with a queued operation after trying to push it. */
enum class SyncOutcome {
    /** Pushed, or resolved well enough that retrying would change nothing — drop the queue row. */
    SUCCESS,
    /** Could not push yet (offline, server down, parent not created yet) — leave it queued. */
    RETRY,
    /** Will never succeed (the entity is gone, or the server rejected it permanently) — give up. */
    DROP
}

suspend fun <T> Result<T, DataError.Remote>.toSyncOutcome(
    onSuccess: suspend (T) -> Unit,
    onConflict: suspend () -> Unit
): SyncOutcome = when (this) {
    is Result.Success -> { onSuccess(data); SyncOutcome.SUCCESS }
    is Result.Error -> when {
        error == DataError.Remote.CONFLICT -> { onConflict(); SyncOutcome.SUCCESS }
        error.isTransient() -> SyncOutcome.RETRY
        else -> SyncOutcome.DROP // permanent error (e.g. NOT_FOUND for a delete) — give up
    }
}

fun EmptyResult<DataError.Remote>.toSyncOutcome(): SyncOutcome = when (this) {
    is Result.Success -> SyncOutcome.SUCCESS
    is Result.Error -> if (error.isTransient()) SyncOutcome.RETRY else SyncOutcome.DROP
}
