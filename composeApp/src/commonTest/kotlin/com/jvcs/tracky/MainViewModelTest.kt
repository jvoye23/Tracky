package com.jvcs.tracky

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.auth.FakeSessionStorage
import com.jvcs.tracky.core.domain.auth.authInfo
import com.jvcs.tracky.features.onboarding.domain.FakeOnboardingStatusStore
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatus
import com.jvcs.tracky.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Where the app opens, and when the in-app hosts switch on. */
internal class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.launchApp(
        sessionStorage: FakeSessionStorage = FakeSessionStorage(),
        statusStore: FakeOnboardingStatusStore = FakeOnboardingStatusStore(),
    ): MainViewModel {
        val viewModel = MainViewModel(sessionStorage, statusStore)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun aFirstLaunchOpensOnTheWelcomeScreen() =
        runTest(dispatcher) {
            val state = launchApp().state.value

            assertThat(state.isCheckingAuth).isFalse()
            assertThat(state.startRoute).isEqualTo(Route.OnboardingRoute.Welcome)
            assertThat(state.hasAppAccess).isFalse()
        }

    @Test
    fun aFreeUserOpensOnTheOverview() =
        runTest(dispatcher) {
            val state = launchApp(statusStore = FakeOnboardingStatusStore(OnboardingStatus.COMPLETED_FREE)).state.value

            assertThat(state.startRoute).isEqualTo(Route.ProjectRoute.ProjectOverview)
            assertThat(state.hasAppAccess).isTrue()
        }

    @Test
    fun aProBuyerWithoutAnAccountReopensOnRegistration() =
        runTest(dispatcher) {
            val state =
                launchApp(statusStore = FakeOnboardingStatusStore(OnboardingStatus.AWAITING_ACCOUNT)).state.value

            assertThat(state.startRoute).isEqualTo(Route.AuthRoute.Register)
            assertThat(state.hasAppAccess).isFalse()
        }

    @Test
    fun aSessionOutranksTheOnboardingStatus() =
        runTest(dispatcher) {
            val state =
                launchApp(
                    sessionStorage = FakeSessionStorage(authInfo()),
                    statusStore = FakeOnboardingStatusStore(OnboardingStatus.AWAITING_ACCOUNT),
                ).state.value

            assertThat(state.startRoute).isEqualTo(Route.ProjectRoute.ProjectOverview)
            assertThat(state.isLoggedIn).isTrue()
            assertThat(state.hasAppAccess).isTrue()
            assertThat(state.username).isEqualTo("user")
            assertThat(state.email).isEqualTo("user@example.com")
        }

    @Test
    fun continuingFreeGrantsAccessWithoutARelaunch() =
        runTest(dispatcher) {
            val statusStore = FakeOnboardingStatusStore()
            val viewModel = launchApp(statusStore = statusStore)

            statusStore.set(OnboardingStatus.COMPLETED_FREE)
            advanceUntilIdle()

            assertThat(viewModel.state.value.hasAppAccess).isTrue()
            // Fixed at launch: the back stack it seeded has already moved on by itself.
            assertThat(viewModel.state.value.startRoute).isEqualTo(Route.OnboardingRoute.Welcome)
        }

    @Test
    fun signingInGrantsAccessWithoutARelaunch() =
        runTest(dispatcher) {
            val sessionStorage = FakeSessionStorage()
            val viewModel = launchApp(sessionStorage = sessionStorage)

            sessionStorage.set(authInfo())
            advanceUntilIdle()

            assertThat(viewModel.state.value.isLoggedIn).isTrue()
            assertThat(viewModel.state.value.hasAppAccess).isTrue()
        }

    @Test
    fun losingTheRefreshTokenEndsTheSessionAndSaysSo() =
        runTest(dispatcher) {
            val sessionStorage = FakeSessionStorage(authInfo())
            val viewModel = launchApp(sessionStorage = sessionStorage)

            viewModel.events.test {
                sessionStorage.set(null)
                assertThat(awaitItem()).isEqualTo(MainEvent.OnSessionExpired)
            }
            assertThat(viewModel.state.value.isLoggedIn).isFalse()
        }

    @Test
    fun startingSignedOutIsNotAnExpiredSession() =
        runTest(dispatcher) {
            val viewModel = launchApp()

            viewModel.events.test {
                advanceUntilIdle()
                expectNoEvents()
            }
        }
}
