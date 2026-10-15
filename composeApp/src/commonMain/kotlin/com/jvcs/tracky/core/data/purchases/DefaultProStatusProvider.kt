package com.jvcs.tracky.core.data.purchases

import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.purchases.EntitlementService
import com.jvcs.tracky.core.domain.purchases.ProAccessRepository
import com.jvcs.tracky.core.domain.purchases.ProStatusProvider
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

    override suspend fun isPro(): Boolean {
        if (proAccessRepository.hasProAccess() == Result.Success(true)) return true
        if (sessionStorage.observeAuthInfo().first() == null) return false
        return entitlementService.current() == Result.Success(true)
    }
}
