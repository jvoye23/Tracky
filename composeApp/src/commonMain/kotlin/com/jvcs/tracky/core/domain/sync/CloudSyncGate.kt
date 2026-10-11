package com.jvcs.tracky.core.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * Whether this device talks to the Tracky server at all.
 *
 * Off means local-only: the free tier keeps every change on the device, so nothing is pushed, nothing
 * is queued for a later push, nothing is pulled and no background sync is scheduled. Every remote edge
 * asks this one gate instead of deciding for itself — a check duplicated per caller is how one path
 * ends up calling the server anyway.
 *
 * Local writes never ask it: the Room write happens either way, and only the step after it does.
 */
interface CloudSyncGate {

    /** Emits the current answer first, then every change. Distinct. */
    fun observeIsEnabled(): Flow<Boolean>

    suspend fun isEnabled(): Boolean
}
