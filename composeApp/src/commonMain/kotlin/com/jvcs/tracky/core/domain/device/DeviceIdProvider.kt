package com.jvcs.tracky.core.domain.device

/**
 * The stable identity of this installation, used to tell this device's rows apart from the ones a
 * user's other devices wrote.
 *
 * It exists for the timer. A running timer is an interval row with no end time, and once the tree
 * syncs across devices such a row may have been opened here or on the user's other phone. The two
 * cases need opposite handling — one is a crash to recover from, the other is a live timer to
 * display — and nothing else in the row distinguishes them.
 *
 * Minted once and kept forever. It is deliberately *not* tied to the session: logging out and back
 * in is still the same device, and a reinstall is legitimately a new one.
 */
interface DeviceIdProvider {

    /**
     * The id, minting and persisting it on first call. Safe to call concurrently; every caller in
     * a process gets the same value.
     */
    suspend fun deviceId(): String
}
