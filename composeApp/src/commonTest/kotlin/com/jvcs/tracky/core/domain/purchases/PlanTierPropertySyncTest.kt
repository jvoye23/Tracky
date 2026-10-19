package com.jvcs.tracky.core.domain.purchases

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.jvcs.tracky.analytics.api.FakeAnalyticsLogger
import com.jvcs.tracky.analytics.api.PlanTierOption
import com.jvcs.tracky.analytics.api.UserProperty
import com.jvcs.tracky.core.domain.auth.FakeSessionStorage
import com.jvcs.tracky.core.domain.auth.authInfo
import com.jvcs.tracky.core.domain.lifecycle.FakeAppLifecycleObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal class PlanTierPropertySyncTest {

    private val analytics = FakeAnalyticsLogger()
    private val proStatusProvider = FakeProStatusProvider(isPro = false)
    private val sessionStorage = FakeSessionStorage()
    private val isInForeground = MutableStateFlow(true)

    // runCurrent, not advanceUntilIdle: the sync collects on backgroundScope, which
    // advanceUntilIdle does not wait for.
    private fun TestScope.startSync() {
        PlanTierPropertySync(
            proStatusProvider = proStatusProvider,
            sessionStorage = sessionStorage,
            appLifecycleObserver = FakeAppLifecycleObserver(isInForeground),
            analyticsLogger = analytics,
            applicationScope = backgroundScope,
        ).start()
        runCurrent()
    }

    private val free = UserProperty.PlanTier(PlanTierOption.FREE)
    private val pro = UserProperty.PlanTier(PlanTierOption.PRO)

    @Test
    fun theTierIsSetAtLaunch() =
        runTest {
            startSync()

            assertThat(analytics.userProperties).containsExactly(free)
        }

    @Test
    fun returningToTheForegroundSetsItAgain() =
        runTest {
            startSync()

            // Pro bought elsewhere, or expired, while the app was away.
            proStatusProvider.isPro = true
            isInForeground.value = false
            runCurrent()
            isInForeground.value = true
            runCurrent()

            assertThat(analytics.userProperties).containsExactly(free, pro)
        }

    @Test
    fun goingToTheBackgroundSetsNothing() =
        runTest {
            startSync()

            isInForeground.value = false
            runCurrent()

            assertThat(analytics.userProperties).containsExactly(free)
        }

    @Test
    fun signingInSetsTheAccountsTier() =
        runTest {
            startSync()

            // The server grants Pro that the store on this device never saw.
            proStatusProvider.isPro = true
            sessionStorage.set(authInfo())
            runCurrent()

            assertThat(analytics.userProperties).containsExactly(free, pro)
        }

    @Test
    fun nothingIsSetInTheBackground() =
        runTest {
            isInForeground.value = false

            startSync()

            assertThat(analytics.userProperties).isEmpty()
        }
}
