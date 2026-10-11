package com.jvcs.tracky

import com.jvcs.tracky.navigation.Route

data class MainState(
    val isLoggedIn: Boolean = false,
    val isCheckingAuth: Boolean = true,
    val username: String? = null,
    val email: String? = null,
    /** Where the back stack starts. Decided once, from what was on disk at launch. */
    val startRoute: Route = Route.OnboardingRoute.Welcome,
    /**
     * Whether the user is past onboarding and inside the app - signed in, or free and local-only.
     * Unlike [startRoute] this follows changes, so the timer UX switches on the moment a free user
     * leaves the paywall.
     */
    val hasAppAccess: Boolean = false,
)
