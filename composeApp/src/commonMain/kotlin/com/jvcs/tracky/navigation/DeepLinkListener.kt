package com.jvcs.tracky.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.koin.compose.koinInject

/**
 * Attaches the back stack to [DeepLinkRouter] for as long as there is one to seed.
 *
 * Keyed on [isLoggedIn] because a tap can arrive before the session read has resolved. While signed
 * out no listener is registered, so the request simply waits in the router until it is.
 */
@Composable
fun DeepLinkListener(
    backStack: NavBackStack<NavKey>,
    isLoggedIn: Boolean,
    deepLinkRouter: DeepLinkRouter = koinInject(),
) {
    DisposableEffect(isLoggedIn, backStack, deepLinkRouter) {
        if (isLoggedIn) {
            // Navigation 3 has no deep-link matcher: linking in means seeding the back stack
            // ourselves. ProjectOverview goes underneath so Back from a cold-start tap lands on
            // the overview rather than dropping the user out of the app.
            deepLinkRouter.listener = { route ->
                backStack.removeAll { true }
                backStack.add(Route.ProjectRoute.ProjectOverview)
                backStack.add(route)
            }
        }
        onDispose { deepLinkRouter.listener = null }
    }
}
