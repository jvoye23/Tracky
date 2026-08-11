package com.jvcs.tracky

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import com.jvcs.tracky.navigation.NavigationRoot
import com.jvcs.tracky.navigation.Route
import com.jvcs.tracky.navigation.routeSavedStateConfiguration
import kotlinx.coroutines.flow.Flow
import org.koin.compose.viewmodel.koinViewModel

@Composable
@Preview
fun App(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onAuthenticationChecked: () -> Unit = {},
    mainViewModel: MainViewModel = koinViewModel()
) {
    val state by mainViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isCheckingAuth) {
        if(!state.isCheckingAuth) {
            onAuthenticationChecked()
        }
    }

    TrackyTheme(
        darkTheme = isDarkTheme
    ) {
        // AppNavHost is withheld until the read of session storages finishes. rememberNavBackStack
        // captures its start destination in a rememberSaveable initializer that runs once
        // and never re-runs, so composing it while isCheckingAuth is still true would pin
        // the back stack to Login even when a valid session exists on disk.

        if (!state.isCheckingAuth) {
            AppNavHost(
                isLoggedIn = state.isLoggedIn,
                events = mainViewModel.events,
            )
        }
    }
}

@Composable
private fun AppNavHost(
    isLoggedIn: Boolean,
    events: Flow<MainEvent>
) {
    val startDestination = if (isLoggedIn) {
        Route.ProjectRoute.ProjectOverview
    } else {
        Route.AuthRoute.Login
    }

    val backStack = rememberNavBackStack(
        configuration = routeSavedStateConfiguration,
        startDestination
    )

    ObserveAsEvents(events) { event ->
        when(event) {
            is MainEvent.OnSessionExpired -> {
                backStack.removeAll { true }
                backStack.add(Route.AuthRoute.Login)
            }
        }
    }

    NavigationRoot(
        backStack = backStack
    )
}
