package com.jvcs.tracky.features.onboarding.presentation.paywall

import androidx.compose.runtime.Immutable
import com.jvcs.tracky.core.domain.purchases.ProPackage
import com.jvcs.tracky.designsystem.util.UiText

// The plans list is never mutated, only replaced by copy().
@Immutable
data class PaywallState(
    val isLoading: Boolean = true,
    /** Already Pro, e.g. a reinstall: there is nothing to sell, only an account to create. */
    val hasProAccess: Boolean = false,
    /** Signed in: Pro needs no account to be created, so the already-Pro screen only closes. */
    val hasAccount: Boolean = false,
    val plans: List<ProPackage> = emptyList(),
    val selectedPlanId: String? = null,
    /** Loading the plans failed; shown with a Retry. */
    val loadError: UiText? = null,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
) {

    /** One store flow at a time: a purchase and a restore racing would both try to own the outcome. */
    val isBusy: Boolean get() = isPurchasing || isRestoring

    val canSubscribe: Boolean get() = selectedPlanId != null && !isBusy
}
