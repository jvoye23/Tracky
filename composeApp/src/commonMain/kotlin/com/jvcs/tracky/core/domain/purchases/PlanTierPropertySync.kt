package com.jvcs.tracky.core.domain.purchases

import com.jvcs.tracky.analytics.api.AnalyticsLogger
import com.jvcs.tracky.analytics.api.PlanTierOption
import com.jvcs.tracky.analytics.api.UserProperty
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.lifecycle.AppLifecycleObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the `plan_tier` analytics user property on what [ProStatusProvider] says.
 *
 * Pro can change while the app is away (bought on another device, expired, refunded) and with the
 * account (the server grants Pro the store never saw), so it is read again on every return to the
 * foreground and every change of signed-in account. A purchase on the paywall changes it without
 * either, so the paywall sets it itself.
 */
class PlanTierPropertySync(
    private val proStatusProvider: ProStatusProvider,
    private val sessionStorage: SessionStorage,
    private val appLifecycleObserver: AppLifecycleObserver,
    private val analyticsLogger: AnalyticsLogger,
    private val applicationScope: CoroutineScope,
) {

    private var started = false

    fun start() {
        if (started) return
        started = true

        applicationScope.launch {
            combine(
                appLifecycleObserver.isInForeground.distinctUntilChanged(),
                sessionStorage.observeAuthInfo().map { it?.user?.id }.distinctUntilChanged(),
            ) { isInForeground, _ -> isInForeground }
                // collectLatest, so a newer trigger abandons a Pro check still waiting on the server.
                .collectLatest { isInForeground ->
                    if (!isInForeground) return@collectLatest
                    val tier = if (proStatusProvider.isPro()) PlanTierOption.PRO else PlanTierOption.FREE
                    analyticsLogger.setUserProperty(UserProperty.PlanTier(tier))
                }
        }
    }
}
