package com.jvcs.tracky.core.data.sync

import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.sync.CloudSyncGate
import com.jvcs.tracky.core.domain.sync.LocalDataImportStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * Sync is on while a session is held - except while this device's local-only data is still waiting
 * to be imported into that session's account.
 *
 * A free user never signs in, so the session is what keeps the free tier off the server. Pro is
 * deliberately not part of it: the server decides Pro from its own record, grandfathered and trial
 * accounts included, and the client has no reliable copy of that.
 *
 * Holding sync back during the import is what keeps it one clean upload. With sync on, an edit to a
 * project the server has not received yet would be pushed as an update to a row it does not have,
 * and pulls and pushes would race the import itself.
 */
class SessionCloudSyncGate(
    private val sessionStorage: SessionStorage,
    private val localDataImportStore: LocalDataImportStore,
) : CloudSyncGate {

    override fun observeIsEnabled(): Flow<Boolean> =
        combine(
            sessionStorage.observeAuthInfo(),
            localDataImportStore.observeIsPending(),
        ) { authInfo, isImportPending ->
            !authInfo?.accessToken.isNullOrBlank() && !isImportPending
        }.distinctUntilChanged()

    override suspend fun isEnabled(): Boolean = observeIsEnabled().first()
}
