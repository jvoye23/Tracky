package com.jvcs.tracky.core.domain.sync

/**
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
    val localIsStrictlyNewer = localUpdatedAtEpochMs != null &&
        (serverUpdatedAtEpochMs == null || localUpdatedAtEpochMs > serverUpdatedAtEpochMs)
    return !localIsStrictlyNewer
}

/**
 * True when the server's interval should replace the local one.
 *
 * Intervals deliberately carry no timestamp (see `TaskInterval.ownUpdatedAt`), so they cannot use
 * the rule above. Instead the open/closed state decides: a local interval with no end time means the
 * timer is running *on this device right now*, and a pull must never close or move it. Closed
 * intervals never change again, so letting the server win on those is safe.
 *
 * @param localEndDateTimeEpochMs the local row's end time, or null if it is still open.
 */
fun serverWinsOnPullForInterval(localEndDateTimeEpochMs: Long?): Boolean =
    localEndDateTimeEpochMs != null
