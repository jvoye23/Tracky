package com.jvcs.tracky.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.jvcs.tracky.core.domain.notification.TimerNotificationPermissionRequester
import com.jvcs.tracky.features.onboarding.presentation.paywall.PaywallScreenRoot
import com.jvcs.tracky.features.onboarding.presentation.paywall.PaywallSource
import com.jvcs.tracky.features.onboarding.presentation.permission.NotificationPermissionScreenRoot
import com.jvcs.tracky.features.onboarding.presentation.welcome.WelcomeScreenRoot
import org.koin.compose.koinInject

/**
 * First-launch onboarding: welcome, notification permission, paywall.
 *
 * The paywall is where the flow forks, and both exits clear the stack: Back must not lead a free user
 * back into onboarding, nor a paying one back to a paywall for something they already own.
 */
fun EntryProviderScope<NavKey>.onboardingEntries(backStack: NavBackStack<NavKey>) {
    entry<Route.OnboardingRoute.Welcome> {
        val permissionRequester = koinInject<TimerNotificationPermissionRequester>()
        WelcomeScreenRoot(
            onGetStartedClick = {
                // Skipped where there is nothing to ask - iOS, or a grant already held - rather than
                // explaining a dialog that never comes. Onboarding is then welcome and paywall only.
                val next =
                    if (permissionRequester.needsPrompt()) {
                        Route.OnboardingRoute.NotificationPermission
                    } else {
                        Route.OnboardingRoute.Paywall(stepsCompleted = 2)
                    }
                backStack.add(next)
            },
            onLoginClick = {
                backStack.add(Route.AuthRoute.Login)
            },
        )
    }

    entry<Route.OnboardingRoute.NotificationPermission> {
        NotificationPermissionScreenRoot(
            onFinish = {
                // Replaced rather than stacked: Back from the paywall goes to the welcome screen, not
                // to a permission that has already been answered.
                backStack.removeLastOrNull()
                backStack.add(Route.OnboardingRoute.Paywall(stepsCompleted = 3))
            },
        )
    }

    entry<Route.OnboardingRoute.Paywall> { route ->
        PaywallScreenRoot(
            source = PaywallSource.Onboarding(stepsCompleted = route.stepsCompleted),
            onContinueFree = {
                backStack.removeAll { true }
                backStack.add(Route.ProjectRoute.ProjectOverview)
            },
            onCreateAccount = {
                backStack.removeAll { true }
                backStack.add(Route.AuthRoute.Register)
            },
            // Onboarding runs without a session, so this only follows a sign-in from elsewhere.
            onClose = {
                backStack.removeAll { true }
                backStack.add(Route.ProjectRoute.ProjectOverview)
            },
        )
    }
}

/**
 * The paywall opened from the drawer, on top of whichever list opened it - closing it, or Back,
 * returns there.
 */
fun EntryProviderScope<NavKey>.proPaywallEntry(backStack: NavBackStack<NavKey>) {
    entry<Route.ProjectRoute.ProPaywall> {
        PaywallScreenRoot(
            source = PaywallSource.App,
            onContinueFree = {
                backStack.removeLastOrNull()
            },
            // A free user who just bought Pro: the same as after onboarding. The stack is cleared and
            // the app stays on registration until the account exists; their projects are imported
            // into it on sign-in.
            onCreateAccount = {
                backStack.removeAll { true }
                backStack.add(Route.AuthRoute.Register)
            },
            onClose = {
                backStack.removeLastOrNull()
            },
        )
    }
}
