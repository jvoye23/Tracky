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
import com.jvcs.tracky.core.domain.util.DataError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private fun TestScope.startSync(times: Int = 1, foreground: Flow<Boolean> = isInForeground) {
        val sync =
            PlanTierPropertySync(
                proStatusProvider = proStatusProvider,
                sessionStorage = sessionStorage,
                appLifecycleObserver = FakeAppLifecycleObserver(foreground),
                analyticsLogger = analytics,
                applicationScope = backgroundScope,
            )
        repeat(times) { sync.start() }
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
    fun aCheckThatCannotTellKeepsTheTierLastSet() =
        runTest {
            proStatusProvider.isPro = true
            startSync()

            // Offline on the next return: the provider cannot say, which must not read as free.
            proStatusProvider.error = DataError.Remote.NO_INTERNET
            isInForeground.value = false
            runCurrent()
            isInForeground.value = true
            runCurrent()

            assertThat(analytics.userProperties).containsExactly(pro)
        }

    @Test
    fun aCheckThatCannotTellAtLaunchSetsNothing() =
        runTest {
            proStatusProvider.error = DataError.Remote.SERVER_ERROR

            startSync()

            assertThat(analytics.userProperties).isEmpty()
        }

    @Test
    fun aTokenRefreshForTheSameAccountChecksNothing() =
        runTest {
            sessionStorage.set(authInfo())
            startSync()

            sessionStorage.set(authInfo(accessToken = "refreshed-token"))
            runCurrent()

            assertThat(analytics.userProperties).containsExactly(free)
        }

    @Test
    fun aRepeatedForegroundSignalChecksNothing() =
        runTest {
            // The platform observers are plain flows, so nothing stops them repeating a value.
            val foreground = MutableSharedFlow<Boolean>(replay = 1)
            foreground.emit(true)
            startSync(foreground = foreground)

            foreground.emit(true)
            runCurrent()

            assertThat(analytics.userProperties).containsExactly(free)
        }

    @Test
    fun startingTwiceStillSetsItOnce() =
        runTest {
            startSync(times = 2)

            assertThat(analytics.userProperties).containsExactly(free)
        }

    @Test
    fun nothingIsSetInTheBackground() =
        runTest {
            isInForeground.value = false

            startSync()

            assertThat(analytics.userProperties).isEmpty()
        }
}
