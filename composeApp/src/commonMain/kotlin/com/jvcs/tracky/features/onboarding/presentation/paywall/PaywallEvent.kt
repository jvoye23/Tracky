package com.jvcs.tracky.features.onboarding.presentation.paywall

import com.jvcs.tracky.designsystem.util.UiText

sealed interface PaywallEvent {

    /** Pro declined: into the app, or back to it when the paywall was opened from there. */
    data object NavigateToApp : PaywallEvent

    /** Pro bought or restored without an account: the account it will sync to comes next. */
    data object NavigateToRegister : PaywallEvent

    /** Pro bought or restored by a signed-in user: nothing left to do but close. */
    data object ProActivated : PaywallEvent

    /** A purchase failed for a reason worth telling: shown in a dialog, the paywall stays. */
    data class ShowPurchaseError(val message: UiText) : PaywallEvent

    /** A restore that did not unlock Pro - nothing found, or it failed: shown in a dialog, the paywall stays. */
    data class ShowRestoreMessage(val message: UiText) : PaywallEvent
}
