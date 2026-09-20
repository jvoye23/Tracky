package com.jvcs.tracky.core.domain.sync

/**
 * How far through the server's change feed this device has read.
 *
 * The cursor is a server-assigned sequence number, not a timestamp. Timestamps on the wire are
 * client-supplied and persisted verbatim, so two devices with skewed clocks routinely write them
 * out of order — a device advancing past a row that was written later but stamped earlier would
 * miss it permanently. Only the server can mint a value that orders its own writes.
 *
 * Persisted rather than held in memory, unlike the throttle `ProjectSyncManager` keeps for the
 * full-tree pull: a cursor that resets on every launch would make every cold start a full resync,
 * which is the cost the change feed exists to avoid.
 */
interface SyncCursorStore {

    /** The last cursor a delta was applied at, or null when this device has never pulled one. */
    suspend fun cursor(): Long?

    /**
     * Records how far a delta was applied.
     *
     * Only ever called after the whole delta has landed in Room. Advancing it first and failing
     * halfway would skip the rest of that change set on every subsequent pull.
     */
    suspend fun setCursor(cursor: Long)

    /**
     * Forgets the cursor, so the next sync starts from scratch.
     *
     * Called on logout: the next account to sign in on this device has its own change feed, and
     * carrying a stale cursor into it would silently skip everything below that sequence number.
     */
    suspend fun clear()
}
