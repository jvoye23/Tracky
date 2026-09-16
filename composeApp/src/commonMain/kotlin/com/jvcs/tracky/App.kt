package com.jvcs.tracky

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import com.jvcs.tracky.core.domain.notification.RequestTimerNotificationPermission
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import com.jvcs.tracky.features.project.domain.timer.RunningTimerRepository
import com.jvcs.tracky.features.project.presentation.stranded_timer.StrandedTimerDialogHost
import com.jvcs.tracky.navigation.DeepLinkListener
import com.jvcs.tracky.navigation.NavigationRoot
import com.jvcs.tracky.navigation.Route
import com.jvcs.tracky.navigation.routeSavedStateConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
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

            // Above the nav host, not inside a screen: the back stack is restored across process
            // death, which is the very thing that strands a timer, so the user can land on any
            // screen. Only once signed in - the review names a project and a task.
            if (state.isLoggedIn) {
                StrandedTimerDialogHost()

                // Asked on the first timer, not at launch: a permission dialog makes sense to a
                // user who has just pressed play, and none at all to one who has just signed in.
                val runningTimerRepository = koinInject<RunningTimerRepository>()
                val hasRunningTimer by remember(runningTimerRepository) {
                    runningTimerRepository.observeRunningTimer().map { it != null }
                }.collectAsStateWithLifecycle(false)
                RequestTimerNotificationPermission(request = hasRunningTimer)
            }
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

    DeepLinkListener(backStack = backStack, isLoggedIn = isLoggedIn)

    NavigationRoot(
        backStack = backStack
    )
}
