package com.jvcs.tracky.core.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * Whether this device holds local-only data that still has to be imported into an account.
 *
 * Set when someone leaves onboarding without an account, because from then on everything they create
 * lives only in Room and the outbox never hears about it. Cleared once [LocalDataImporter] has
 * uploaded it, and on logout, which wipes it.
 */
interface LocalDataImportStore {

    /** Emits the stored value first, then every change. */
    fun observeIsPending(): Flow<Boolean>

    suspend fun setPending(isPending: Boolean)

    /**
     * Titles of projects the server refused for good during the import, so they stayed on this device
     * only. Empty when there are none, or once the user has been told.
     */
    fun observeRefusedProjects(): Flow<List<String>>

    suspend fun setRefusedProjects(titles: List<String>)
}
