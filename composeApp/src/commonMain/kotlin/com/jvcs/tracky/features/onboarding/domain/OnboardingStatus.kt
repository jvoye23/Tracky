package com.jvcs.tracky.features.onboarding.domain

/** How far the user got through first-launch onboarding. Decides where the app opens. */
enum class OnboardingStatus {
    /** Never finished it: the app opens on the welcome screen. */
    NOT_STARTED,

    /** Declined Pro on the paywall: the app opens on the overview, local-only. */
    COMPLETED_FREE,

    /**
     * Paid for Pro but has no account yet. The app reopens on registration until a session exists,
     * because the subscription is worth nothing across devices without one.
     */
    AWAITING_ACCOUNT,
}
