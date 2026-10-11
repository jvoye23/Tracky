package com.jvcs.tracky.features.onboarding.presentation.paywall

sealed interface PaywallAction {

    data class OnPlanClick(val planId: String) : PaywallAction

    data object OnSubscribeClick : PaywallAction

    /** "Continue with Free" and the close button: both decline Pro. */
    data object OnContinueFreeClick : PaywallAction

    data object OnRestoreClick : PaywallAction

    data object OnRetryClick : PaywallAction

    /** Already Pro: on to creating the account, or closing when there is one. */
    data object OnContinueProClick : PaywallAction
}
