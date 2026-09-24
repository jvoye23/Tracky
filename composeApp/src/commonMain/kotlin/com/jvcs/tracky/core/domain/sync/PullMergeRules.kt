package com.jvcs.tracky.core.domain.sync

/*
 * Decides, row by row, whether a pull from the server may overwrite what is already in Room.
 *
 * `GET /api/projects` returns the whole tree, so a plain server-wins upsert would quietly destroy
 * anything edited locally but not yet pushed — and then the queued push would send the server's own
 * stale row back, making the loss permanent.
 */

/**
 * True when the server's row should replace the local one.
 *
 * The local row wins only when it is strictly newer, which is the same rule
 * `resolveProjectConflict` / `resolveTaskConflict` apply in the other direction. A local row with no
 * stamp at all has never been written locally in a way worth defending, so the server wins; ties go
 * to the server because it is canonical.
 */
fun serverWinsOnPull(localUpdatedAtEpochMs: Long?, serverUpdatedAtEpochMs: Long?): Boolean {
    val localIsStrictlyNewer =
        localUpdatedAtEpochMs != null &&
            (serverUpdatedAtEpochMs == null || localUpdatedAtEpochMs > serverUpdatedAtEpochMs)
    return !localIsStrictlyNewer
}

/**
 * True when the server's interval should replace the local one.
 *
 * Intervals carry no timestamp of their own (see `TaskInterval.ownUpdatedAt`), so they cannot use
 * the rule above. What matters instead is whether this device still owes the server a change.
 *
 * The rule used to be "the server wins only between two closed rows". That was a proxy for the one
 * case worth defending — an offline stop that has not drained yet, which would be reopened by the
 * server's stale copy and then pushed back reopened, losing the banked duration permanently. But
 * the proxy also refused the case cross-device sync exists for: the user stopped the timer on their
 * tablet, and the closed row arriving from the server is the only way this device will ever hear
 * about it. Under the old rule a locally-open interval could never be closed by a pull, so the
 * timer would tick here forever.
 *
 * `pending_sync_operations` answers the real question exactly, so the proxy is gone. A locally-open
 * interval with nothing queued has no unsent change to protect, and the server is canonical.
 *
 * The one rule kept unconditionally is that a pull must never *reopen* a closed row. A server copy
 * that is still open is the server not having heard the stop yet, never news.
 *
 * @param localEndDateTimeEpochMs the local row's end time, or null if it is still open.
 * @param serverEndDateTimeEpochMs the incoming row's end time, or null if the server still has it open.
 * @param hasPendingLocalPush whether `pending_sync_operations` holds an operation for this interval.
 */
fun serverWinsOnPullForInterval(
    localEndDateTimeEpochMs: Long?,
    serverEndDateTimeEpochMs: Long?,
    hasPendingLocalPush: Boolean,
): Boolean =
    when {
        // This device owes the server a change; its own state is newer by definition.
        hasPendingLocalPush -> false

        // Reopening a closed interval discards its banked duration.
        localEndDateTimeEpochMs != null && serverEndDateTimeEpochMs == null -> false

        else -> true
    }
