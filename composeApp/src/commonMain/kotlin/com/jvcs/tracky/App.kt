package com.jvcs.tracky

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.util.ObserveAsEvents
import com.jvcs.tracky.features.project.presentation.strandedtimer.StrandedTimerDialogHost
import com.jvcs.tracky.features.project.presentation.timerpermission.TimerNotificationPermissionDialogHost
import com.jvcs.tracky.navigation.DeepLinkListener
import com.jvcs.tracky.navigation.NavigationRoot
import com.jvcs.tracky.navigation.Route
import com.jvcs.tracky.navigation.routeSavedStateConfiguration
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onAuthenticationCheck: () -> Unit = {},
    mainViewModel: MainViewModel = koinViewModel(),
) {
    val state by mainViewModel.state.collectAsStateWithLifecycle()

    val currentOnAuthenticationCheck by rememberUpdatedState(onAuthenticationCheck)
    LaunchedEffect(state.isCheckingAuth) {
        if (!state.isCheckingAuth) {
            currentOnAuthenticationCheck()
        }
    }

    TrackyTheme(
        darkTheme = isDarkTheme,
    ) {
        // AppNavHost is withheld until the read of session storages finishes. rememberNavBackStack
        // captures its start destination in a rememberSaveable initializer that runs once
        // and never re-runs, so composing it while isCheckingAuth is still true would pin
        // the back stack to Login even when a valid session exists on disk.

        if (!state.isCheckingAuth) {
            val startDestination =
                if (state.isLoggedIn) {
                    Route.ProjectRoute.ProjectOverview
                } else {
                    Route.AuthRoute.Login
                }
            val backStack =
                rememberNavBackStack(
                    configuration = routeSavedStateConfiguration,
                    startDestination,
                )

            ObserveAsEvents(mainViewModel.events) { event ->
                when (event) {
                    is MainEvent.OnSessionExpired -> {
                        backStack.removeAll { true }
                        backStack.add(Route.AuthRoute.Login)
                    }
                }
            }

            AppNavHost(
                backStack = backStack,
                isLoggedIn = state.isLoggedIn,
            )

            // Above the nav host, not inside a screen: the back stack is restored across process
            // death, which is the very thing that strands a timer, so the user can land on any
            // screen. Only once signed in - the review names a project and a task.
            if (state.isLoggedIn) {
                StrandedTimerDialogHost()

                // Asked on the first timer, not at launch: a permission dialog makes sense to a
                // user who has just pressed play, and none at all to one who has just signed in.
                TimerNotificationPermissionDialogHost()
            }
        }
    }
}

@Composable
private fun AppNavHost(backStack: NavBackStack<NavKey>, isLoggedIn: Boolean) {
    DeepLinkListener(backStack = backStack, isLoggedIn = isLoggedIn)

    NavigationRoot(
        backStack = backStack,
    )
}
