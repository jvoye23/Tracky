package com.jvcs.tracky.core.domain.purchases

import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.identity.AppUserIdStore
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Keeps the app user id, and so the RevenueCat customer, in step with the signed-in Tracky account.
 *
 * RevenueCat is configured with [AppUserIdStore]'s id from launch, and sign-up sends that same id
 * to the server. So registering on this device changes nothing here. Only two transitions do:
 *
 * - **Signing in to an existing account** brings a server id that differs from the local one. The
 *   account's id wins. The customer switches to it, and a purchase made on the old id is moved over
 *   with a sync.
 * - **Losing the session** (logout or expiry) mints a fresh id, so the next purchase on this device
 *   is not credited to the account that left. It switches rather than logging out, because
 *   RevenueCat's logout would fall back to an anonymous id the server cannot use.
 *
 * **The order of switching and storing is chosen so that a process death mid-way heals itself.**
 * A failed switch is retried with backoff, and a newer session change abandons it, but the app may
 * be killed before it succeeds. At the next launch [AppUserIdStore]'s id configures RevenueCat, so:
 *
 * - Signing in **switches first and stores second**. Killed in between, the stored id still differs
 *   from the account's at the next launch, so the switch and the purchase sync simply run again.
 * - Losing the session **stores first and switches second**. Killed in between, the next launch
 *   configures RevenueCat with the fresh id. Switched first, nothing would remember the logout, and
 *   the next person's purchase would go to the account that left.
 *
 * Watching the session here, rather than calling from the login screens, covers every way a session
 * appears: email, Google, Apple, or a session restored at launch.
 */
class AppUserIdentitySync(
    private val sessionStorage: SessionStorage,
    private val appUserIdStore: AppUserIdStore,
    private val proAccessRepository: ProAccessRepository,
    private val applicationScope: CoroutineScope,
) {

    private var started = false
    private var hadSession = false

    fun start() {
        if (started) return
        started = true

        applicationScope.launch {
            sessionStorage
                .observeAuthInfo()
                .map { it?.user?.id }
                .distinctUntilChanged()
                // collectLatest, so a newer session change abandons a switch still being retried.
                .collectLatest { userId ->
                    when {
                        userId != null -> {
                            hadSession = true
                            if (userId != appUserIdStore.get()) {
                                switchCustomerTo(userId)
                                appUserIdStore.replace(userId)
                                proAccessRepository.syncPurchases()
                            }
                        }

                        // Only after a session existed. A launch without one keeps its id, because
                        // that id may already carry a purchase that is waiting for sign-up.
                        hadSession -> {
                            hadSession = false
                            val freshId = Uuid.random().toString()
                            appUserIdStore.replace(freshId)
                            switchCustomerTo(freshId)
                        }
                    }
                }
        }
    }

    /** Switches the RevenueCat customer to [appUserId], retrying until it works. */
    private suspend fun switchCustomerTo(appUserId: String) {
        var retryDelay = INITIAL_RETRY_DELAY
        while (proAccessRepository.identify(appUserId) is Result.Error) {
            Logger.withTag(TAG).w { "switching the store customer failed, retrying in $retryDelay" }
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
        }
    }

    private companion object {
        const val TAG = "AppUserIdentitySync"
        val INITIAL_RETRY_DELAY: Duration = 5.seconds
        val MAX_RETRY_DELAY: Duration = 5.minutes
    }
}
