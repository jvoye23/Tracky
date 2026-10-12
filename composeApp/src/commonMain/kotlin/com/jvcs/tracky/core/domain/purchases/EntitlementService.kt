package com.jvcs.tracky.core.domain.purchases

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result

/**
 * The server's own record of whether the account is Pro. It gates sync, and it can fall behind the
 * store: it learns about purchases from RevenueCat's webhook, which can arrive late or not be
 * understood at all.
 */
interface EntitlementService {

    /**
     * Asks the server to re-read the account's entitlement from RevenueCat and store it. Success
     * carries whether the server now says Pro. The server rate-limits this per account, and a
     * second call too soon fails with [DataError.Remote.TOO_MANY_REQUESTS].
     */
    suspend fun refresh(): Result<Boolean, DataError.Remote>
}
