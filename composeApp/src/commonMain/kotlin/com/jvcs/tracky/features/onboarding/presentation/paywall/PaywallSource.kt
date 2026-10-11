package com.jvcs.tracky.features.onboarding.presentation.paywall

/** Where the paywall was opened from, which decides what leaving it means. */
enum class PaywallSource {
    /**
     * The last onboarding step. Leaving decides where the app opens from now on, so the choice is
     * persisted as the onboarding status.
     */
    ONBOARDING,

    /**
     * The navigation drawer. The user is already inside the app, so leaving only closes the screen
     * and the onboarding status is left alone.
     */
    APP,
}
