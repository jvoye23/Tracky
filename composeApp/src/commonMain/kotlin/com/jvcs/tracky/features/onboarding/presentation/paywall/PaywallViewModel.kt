package com.jvcs.tracky.features.onboarding.presentation.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.purchases.ProAccessRepository
import com.jvcs.tracky.core.domain.purchases.ProPackage
import com.jvcs.tracky.core.domain.purchases.ProPackageType
import com.jvcs.tracky.core.domain.purchases.PurchaseError
import com.jvcs.tracky.core.domain.sync.LocalDataImportStore
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.onFailure
import com.jvcs.tracky.core.domain.util.onSuccess
import com.jvcs.tracky.designsystem.util.UiText
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatus
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatusStore
import com.jvcs.tracky.features.project.presentation.util.toUiText
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.paywall_plan_unavailable
import tracky.composeapp.generated.resources.restore_nothing_found

/**
 * Pro, or free - as the last onboarding step, or opened from the drawer.
 *
 * Choices are persisted before the navigation event goes out, so a process death in between reopens
 * the app where the choice put it rather than back on the paywall:
 * - Free, from onboarding: the app opens on the overview from now on, and the device's data is
 *   marked for import should it ever get an account. Free from the drawer only closes the screen.
 * - Pro without an account, from either place: the app is pinned to registration until an account
 *   exists, and the local data is marked for import into it.
 * - Pro with an account: nothing to persist; the purchase is already linked to it.
 */
class PaywallViewModel(
    private val source: PaywallSource,
    private val proAccessRepository: ProAccessRepository,
    private val onboardingStatusStore: OnboardingStatusStore,
    private val sessionStorage: SessionStorage,
    private val localDataImportStore: LocalDataImportStore,
) : ViewModel() {

    private val eventChannel = Channel<PaywallEvent>()
    val events = eventChannel.receiveAsFlow()

    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(PaywallState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    load()
                    hasLoadedInitialData = true
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), PaywallState())

    fun onAction(action: PaywallAction) {
        when (action) {
            is PaywallAction.OnPlanClick -> {
                _state.update {
                    it.copy(selectedPlanId = action.planId)
                }
            }

            PaywallAction.OnSubscribeClick -> {
                purchaseSelectedPlan()
            }

            PaywallAction.OnContinueFreeClick -> {
                continueFree()
            }

            PaywallAction.OnRestoreClick -> {
                restorePurchases()
            }

            PaywallAction.OnRetryClick -> {
                load()
            }

            PaywallAction.OnContinueProClick -> {
                finishWithPro()
            }
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val proAccess = async { proAccessRepository.hasProAccess() }
            val offerings = async { proAccessRepository.getOfferings() }
            val hasAccount = hasAccount()

            // Unknown Pro status reads as "not Pro": the paywall stays usable, and the store refuses
            // a second purchase of the same subscription anyway.
            val hasProAccess = (proAccess.await() as? Result.Success)?.data ?: false
            when (val result = offerings.await()) {
                is Result.Success -> {
                    val plans = result.data
                    _state.update { state ->
                        state.copy(
                            isLoading = false,
                            hasProAccess = hasProAccess,
                            hasAccount = hasAccount,
                            plans = plans,
                            selectedPlanId =
                                state.selectedPlanId?.takeIf { id -> plans.any { it.identifier == id } }
                                    ?: plans.defaultSelection()?.identifier,
                        )
                    }
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasProAccess = hasProAccess,
                            hasAccount = hasAccount,
                            loadError = result.error.toUiText(),
                        )
                    }
                }
            }
        }
    }

    private fun purchaseSelectedPlan() {
        val current = _state.value
        if (!current.canSubscribe) return
        val plan = current.plans.firstOrNull { it.identifier == current.selectedPlanId } ?: return

        _state.update { it.copy(isPurchasing = true) }
        viewModelScope.launch {
            proAccessRepository
                .purchase(plan)
                .onSuccess { hasProAccess ->
                    if (hasProAccess) {
                        finishWithPro()
                    } else {
                        // Paid, but the entitlement is not active: a dashboard misconfiguration rather
                        // than anything the user did. Never send them on without Pro.
                        _state.update { it.copy(isPurchasing = false) }
                        eventChannel.send(PaywallEvent.ShowPurchaseError(DataError.Remote.UNKNOWN.toUiText()))
                    }
                }.onFailure { error ->
                    _state.update { it.copy(isPurchasing = false) }
                    error.toPurchaseErrorText()?.let { eventChannel.send(PaywallEvent.ShowPurchaseError(it)) }
                    // The selected plan vanished from the offering; show what is on offer now.
                    if (error == PurchaseError.PackageNotFound) load()
                }
        }
    }

    // Only ever reached from the Restore button: restorePurchases may show OS sign-in prompts.
    private fun restorePurchases() {
        if (_state.value.isBusy) return
        _state.update { it.copy(isRestoring = true) }
        viewModelScope.launch {
            proAccessRepository
                .restorePurchases()
                .onSuccess { hasProAccess ->
                    if (hasProAccess) {
                        finishWithPro()
                    } else {
                        _state.update { it.copy(isRestoring = false) }
                        eventChannel.send(
                            PaywallEvent.ShowRestoreMessage(UiText.Resource(Res.string.restore_nothing_found)),
                        )
                    }
                }.onFailure { error ->
                    _state.update { it.copy(isRestoring = false) }
                    eventChannel.send(PaywallEvent.ShowRestoreMessage(error.toUiText()))
                }
        }
    }

    private fun continueFree() {
        viewModelScope.launch {
            if (source == PaywallSource.ONBOARDING && !hasAccount()) {
                onboardingStatusStore.set(OnboardingStatus.COMPLETED_FREE)
                // Everything created from here on lives only on this device until an account exists.
                localDataImportStore.setPending(true)
            }
            eventChannel.send(PaywallEvent.NavigateToApp)
        }
    }

    private fun finishWithPro() {
        viewModelScope.launch {
            when {
                // The purchase is already linked to the signed-in account, so there is nothing left to
                // do but close.
                hasAccount() -> {
                    eventChannel.send(PaywallEvent.ProActivated)
                }

                // From onboarding or the drawer alike: Pro without an account is pinned to registration
                // until one exists, and whatever this device holds is imported into it on sign-in.
                else -> {
                    onboardingStatusStore.set(OnboardingStatus.AWAITING_ACCOUNT)
                    localDataImportStore.setPending(true)
                    eventChannel.send(PaywallEvent.NavigateToRegister)
                }
            }
        }
    }

    private suspend fun hasAccount(): Boolean = sessionStorage.observeAuthInfo().first() != null

    /** Annual first - it is the plan the paywall leads with - then whatever comes first. */
    private fun List<ProPackage>.defaultSelection(): ProPackage? =
        firstOrNull { it.type == ProPackageType.ANNUAL } ?: firstOrNull()

    /** Null for a cancel: the user closed the store sheet on purpose, so there is nothing to explain. */
    private fun PurchaseError.toPurchaseErrorText(): UiText? =
        when (this) {
            PurchaseError.Cancelled -> null
            PurchaseError.PackageNotFound -> UiText.Resource(Res.string.paywall_plan_unavailable)
            is PurchaseError.Remote -> error.toUiText()
        }
}
