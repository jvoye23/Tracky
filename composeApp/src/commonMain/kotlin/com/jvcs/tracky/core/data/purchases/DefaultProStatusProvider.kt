package com.jvcs.tracky.core.data.purchases

import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.purchases.EntitlementService
import com.jvcs.tracky.core.domain.purchases.ProAccessRepository
import com.jvcs.tracky.core.domain.purchases.ProStatusProvider
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.flow.first

/**
 * The store first: RevenueCat answers from its cached customer info, so this is quick and works
 * offline. The server only when the store says no and there is an account, because the server can
 * grant Pro that RevenueCat never sees - a grandfathered account, a trial, or one granted by hand.
 */
class DefaultProStatusProvider(
    private val proAccessRepository: ProAccessRepository,
    private val entitlementService: EntitlementService,
    private val sessionStorage: SessionStorage,
) : ProStatusProvider {

    override suspend fun proStatus(): Result<Boolean, DataError.Remote> {
        val store = proAccessRepository.hasProAccess()
        if (store == Result.Success(true)) return store
        // Without an account the store is the only source, so its answer, or its failure, stands.
        if (sessionStorage.observeAuthInfo().first() == null) return store

        val server = entitlementService.current()
        return when {
            server == Result.Success(true) -> server

            // The server says free, but a store that did not answer may still hold Pro.
            server is Result.Success && store is Result.Error -> store

            else -> server
        }
    }
}
