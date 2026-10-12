package com.jvcs.tracky.features.onboarding.presentation.paywall

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.auth.FakeSessionStorage
import com.jvcs.tracky.core.domain.auth.authInfo
import com.jvcs.tracky.core.domain.purchases.FakeEntitlementService
import com.jvcs.tracky.core.domain.purchases.FakeProAccessRepository
import com.jvcs.tracky.core.domain.purchases.ProPackage
import com.jvcs.tracky.core.domain.purchases.ProPackageType
import com.jvcs.tracky.core.domain.purchases.PurchaseError
import com.jvcs.tracky.core.domain.sync.FakeLocalDataImportStore
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.designsystem.util.UiText
import com.jvcs.tracky.features.onboarding.domain.FakeOnboardingStatusStore
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatus
import com.jvcs.tracky.features.project.presentation.util.toUiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.paywall_plan_unavailable
import tracky.composeapp.generated.resources.restore_nothing_found
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class PaywallViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val monthly = ProPackage("monthly", ProPackageType.MONTHLY, "Pro Monthly", "€3.99")
    private val annual = ProPackage("annual", ProPackageType.ANNUAL, "Pro Yearly", "€29.99")
    private val repository = FakeProAccessRepository(offeringsResult = Result.Success(listOf(monthly, annual)))
    private val statusStore = FakeOnboardingStatusStore()
    private val sessionStorage = FakeSessionStorage()
    private val importStore = FakeLocalDataImportStore()
    private val entitlementService = FakeEntitlementService()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Subscribes, since the state is WhileSubscribed, and lets the first load settle. */
    private fun TestScope.openScreen(source: PaywallSource = PaywallSource.ONBOARDING): PaywallViewModel {
        val viewModel =
            PaywallViewModel(source, repository, statusStore, sessionStorage, importStore, entitlementService)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun theScreenIsLoadingUntilThePlansArrive() {
        val viewModel =
            PaywallViewModel(
                PaywallSource.ONBOARDING,
                repository,
                statusStore,
                sessionStorage,
                importStore,
                entitlementService,
            )

        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    @Test
    fun thePlansLoadWithTheYearlyPlanPreselected() =
        runTest(dispatcher) {
            val state = openScreen().state.value

            assertThat(state.isLoading).isFalse()
            assertThat(state.plans).containsExactly(monthly, annual)
            assertThat(state.selectedPlanId).isEqualTo("annual")
            assertThat(state.canSubscribe).isTrue()
        }

    @Test
    fun withoutAYearlyPlanTheFirstOneIsPreselected() =
        runTest(dispatcher) {
            repository.offeringsResult = Result.Success(listOf(monthly))

            assertThat(openScreen().state.value.selectedPlanId).isEqualTo("monthly")
        }

    @Test
    fun aFailedLoadShowsItsErrorAndRetryLoadsAgain() =
        runTest(dispatcher) {
            repository.offeringsResult = Result.Error(DataError.Remote.NO_INTERNET)
            val viewModel = openScreen()
            assertThat(viewModel.state.value.loadError).isEqualTo(DataError.Remote.NO_INTERNET.toUiText())

            repository.offeringsResult = Result.Success(listOf(annual))
            viewModel.onAction(PaywallAction.OnRetryClick)
            advanceUntilIdle()

            assertThat(viewModel.state.value.loadError).isNull()
            assertThat(viewModel.state.value.plans).containsExactly(annual)
        }

    @Test
    fun aCustomerWhoAlreadyHasProIsOfferedTheAccountInstead() =
        runTest(dispatcher) {
            repository.result = Result.Success(true)

            assertThat(openScreen().state.value.hasProAccess).isTrue()
        }

    @Test
    fun anUnknownProStatusLeavesThePaywallUsable() =
        runTest(dispatcher) {
            repository.result = Result.Error(DataError.Remote.NO_INTERNET)
            val state = openScreen().state.value

            assertThat(state.hasProAccess).isFalse()
            assertThat(state.canSubscribe).isTrue()
        }

    @Test
    fun subscribingWhileARestoreRunsIsIgnored() =
        runTest(dispatcher) {
            val viewModel = openScreen()

            viewModel.onAction(PaywallAction.OnRestoreClick)
            viewModel.onAction(PaywallAction.OnSubscribeClick)
            advanceUntilIdle()

            assertThat(repository.purchasedPackages).isEmpty()
        }

    @Test
    fun tappingAPlanSelectsIt() =
        runTest(dispatcher) {
            val viewModel = openScreen()

            viewModel.onAction(PaywallAction.OnPlanClick("monthly"))
            advanceUntilIdle()

            assertThat(viewModel.state.value.selectedPlanId).isEqualTo("monthly")
        }

    @Test
    fun aPurchaseThatUnlocksProMovesOnToRegistration() =
        runTest(dispatcher) {
            val viewModel = openScreen()

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnSubscribeClick)
                assertThat(awaitItem()).isEqualTo(PaywallEvent.NavigateToRegister)
            }
            assertThat(repository.purchasedPackages).containsExactly(annual)
            assertThat(statusStore.current).isEqualTo(OnboardingStatus.AWAITING_ACCOUNT)
            assertThat(importStore.isPending).isTrue()
            // There is no account to refresh yet; registration reads the entitlement instead.
            assertThat(entitlementService.refreshCalls).isEqualTo(0)
        }

    @Test
    fun aCancelledPurchaseStaysQuietlyOnThePaywall() =
        runTest(dispatcher) {
            repository.purchaseResult = Result.Error(PurchaseError.Cancelled)
            val viewModel = openScreen()

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnSubscribeClick)
                advanceUntilIdle()
                expectNoEvents()
            }
            assertThat(viewModel.state.value.isPurchasing).isFalse()
            assertThat(statusStore.current).isEqualTo(OnboardingStatus.NOT_STARTED)
        }

    @Test
    fun aFailedPurchaseShowsWhyAndStays() =
        runTest(dispatcher) {
            repository.purchaseResult = Result.Error(PurchaseError.Remote(DataError.Remote.NO_INTERNET))
            val viewModel = openScreen()

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnSubscribeClick)
                assertThat(awaitItem())
                    .isEqualTo(PaywallEvent.ShowPurchaseError(DataError.Remote.NO_INTERNET.toUiText()))
            }
            assertThat(viewModel.state.value.isPurchasing).isFalse()
            assertThat(statusStore.current).isEqualTo(OnboardingStatus.NOT_STARTED)
        }

    @Test
    fun aPlanThatVanishedSaysSoAndReloadsThePlans() =
        runTest(dispatcher) {
            repository.purchaseResult = Result.Error(PurchaseError.PackageNotFound)
            val viewModel = openScreen()
            repository.offeringsResult = Result.Success(listOf(monthly))

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnSubscribeClick)
                assertThat(awaitItem())
                    .isEqualTo(PaywallEvent.ShowPurchaseError(UiText.Resource(Res.string.paywall_plan_unavailable)))
            }
            advanceUntilIdle()
            assertThat(viewModel.state.value.plans).containsExactly(monthly)
            assertThat(viewModel.state.value.selectedPlanId).isEqualTo("monthly")
        }

    @Test
    fun aPaidPurchaseWithoutTheEntitlementNeverMovesOn() =
        runTest(dispatcher) {
            repository.purchaseResult = Result.Success(false)
            val viewModel = openScreen()

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnSubscribeClick)
                assertThat(awaitItem())
                    .isEqualTo(PaywallEvent.ShowPurchaseError(DataError.Remote.UNKNOWN.toUiText()))
            }
            assertThat(viewModel.state.value.isPurchasing).isFalse()
            assertThat(statusStore.current).isEqualTo(OnboardingStatus.NOT_STARTED)
        }

    @Test
    fun continuingFreeGoesStraightIntoTheAppAndIsRemembered() =
        runTest(dispatcher) {
            val viewModel = openScreen()

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnContinueFreeClick)
                assertThat(awaitItem()).isEqualTo(PaywallEvent.NavigateToApp)
            }
            assertThat(statusStore.current).isEqualTo(OnboardingStatus.COMPLETED_FREE)
            // From here on everything is local-only, to be imported should an account ever exist.
            assertThat(importStore.isPending).isTrue()
        }

    @Test
    fun aRestoreThatFindsProMovesOnToRegistration() =
        runTest(dispatcher) {
            repository.restoreResult = Result.Success(true)
            val viewModel = openScreen()

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnRestoreClick)
                assertThat(awaitItem()).isEqualTo(PaywallEvent.NavigateToRegister)
            }
            assertThat(statusStore.current).isEqualTo(OnboardingStatus.AWAITING_ACCOUNT)
        }

    @Test
    fun aRestoreThatFindsNothingSaysSo() =
        runTest(dispatcher) {
            val viewModel = openScreen()

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnRestoreClick)
                assertThat(awaitItem())
                    .isEqualTo(PaywallEvent.ShowRestoreMessage(UiText.Resource(Res.string.restore_nothing_found)))
            }
            assertThat(viewModel.state.value.isRestoring).isFalse()
        }

    @Test
    fun aFailedRestoreSaysWhy() =
        runTest(dispatcher) {
            repository.restoreResult = Result.Error(DataError.Remote.NO_INTERNET)
            val viewModel = openScreen()

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnRestoreClick)
                assertThat(awaitItem())
                    .isEqualTo(PaywallEvent.ShowRestoreMessage(DataError.Remote.NO_INTERNET.toUiText()))
            }
            assertThat(viewModel.state.value.isRestoring).isFalse()
        }

    @Test
    fun aSecondRestoreTapWhileOneRunsIsIgnored() =
        runTest(dispatcher) {
            val viewModel = openScreen()

            viewModel.onAction(PaywallAction.OnRestoreClick)
            viewModel.onAction(PaywallAction.OnRestoreClick)
            advanceUntilIdle()

            assertThat(repository.restoreCalls).isEqualTo(1)
        }

    @Test
    fun alreadyProContinuesToRegistration() =
        runTest(dispatcher) {
            repository.result = Result.Success(true)
            val viewModel = openScreen()

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnContinueProClick)
                assertThat(awaitItem()).isEqualTo(PaywallEvent.NavigateToRegister)
            }
        }

    // --- Opened from the drawer -------------------------------------------------------------------

    @Test
    fun fromTheAppContinuingFreeClosesWithoutTouchingTheOnboardingStatus() =
        runTest(dispatcher) {
            statusStore.set(OnboardingStatus.COMPLETED_FREE)
            val viewModel = openScreen(PaywallSource.APP)

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnContinueFreeClick)
                assertThat(awaitItem()).isEqualTo(PaywallEvent.NavigateToApp)
            }
            assertThat(statusStore.current).isEqualTo(OnboardingStatus.COMPLETED_FREE)
            assertThat(importStore.isPending).isFalse()
        }

    @Test
    fun fromTheAppAFreeUserWhoBuysProIsPinnedToRegistrationLikeAfterOnboarding() =
        runTest(dispatcher) {
            statusStore.set(OnboardingStatus.COMPLETED_FREE)
            val viewModel = openScreen(PaywallSource.APP)

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnSubscribeClick)
                assertThat(awaitItem()).isEqualTo(PaywallEvent.NavigateToRegister)
            }
            assertThat(statusStore.current).isEqualTo(OnboardingStatus.AWAITING_ACCOUNT)
            assertThat(importStore.isPending).isTrue()
        }

    @Test
    fun aSignedInUserWhoBuysProJustCloses() =
        runTest(dispatcher) {
            sessionStorage.set(authInfo())
            val viewModel = openScreen(PaywallSource.APP)

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnSubscribeClick)
                assertThat(awaitItem()).isEqualTo(PaywallEvent.ProActivated)
            }
            assertThat(statusStore.current).isEqualTo(OnboardingStatus.NOT_STARTED)
            // A signed-in device already syncs; there is nothing local-only to import.
            assertThat(importStore.isPending).isFalse()
            // The server hears about the purchase now rather than when the webhook lands.
            assertThat(entitlementService.refreshCalls).isEqualTo(1)
        }

    @Test
    fun theServerIsRefreshedBeforeThePaywallCloses() =
        runTest(dispatcher) {
            sessionStorage.set(authInfo())
            entitlementService.latency = 2.seconds
            val viewModel = openScreen(PaywallSource.APP)

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnSubscribeClick)
                assertThat(awaitItem()).isEqualTo(PaywallEvent.ProActivated)
                // Closed only once the refresh answered. Closing first would let the overview's first
                // sync reach the server before it knows, and its own refresh would be rate-limited.
                assertThat(currentTime).isEqualTo(2.seconds.inWholeMilliseconds)
            }
        }

    @Test
    fun aSlowRefreshDoesNotHoldAPaidUserOnThePaywall() =
        runTest(dispatcher) {
            sessionStorage.set(authInfo())
            entitlementService.latency = 1.minutes
            val viewModel = openScreen(PaywallSource.APP)

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnSubscribeClick)
                assertThat(awaitItem()).isEqualTo(PaywallEvent.ProActivated)
            }
            assertThat(currentTime).isLessThanOrEqualTo(5.seconds.inWholeMilliseconds)
        }

    @Test
    fun aSignedInUserStillClosesWhenTheServerCannotBeRefreshed() =
        runTest(dispatcher) {
            sessionStorage.set(authInfo())
            entitlementService.result = Result.Error(DataError.Remote.SERVICE_UNAVAILABLE)
            val viewModel = openScreen(PaywallSource.APP)

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnSubscribeClick)
                assertThat(awaitItem()).isEqualTo(PaywallEvent.ProActivated)
            }
        }

    @Test
    fun aSignedInUserIsKnownToHaveAnAccount() =
        runTest(dispatcher) {
            sessionStorage.set(authInfo())

            assertThat(openScreen(PaywallSource.APP).state.value.hasAccount).isTrue()
        }

    @Test
    fun aSignedInUserContinuingFreeFromOnboardingIsNotMadeLocalOnly() =
        runTest(dispatcher) {
            sessionStorage.set(authInfo())
            val viewModel = openScreen()

            viewModel.events.test {
                viewModel.onAction(PaywallAction.OnContinueFreeClick)
                assertThat(awaitItem()).isEqualTo(PaywallEvent.NavigateToApp)
            }
            assertThat(statusStore.current).isEqualTo(OnboardingStatus.NOT_STARTED)
            assertThat(importStore.isPending).isFalse()
        }
}
