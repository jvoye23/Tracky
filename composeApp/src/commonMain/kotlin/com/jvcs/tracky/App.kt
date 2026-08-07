package com.jvcs.tracky

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import com.jvcs.tracky.navigation.NavigationRoot
import com.jvcs.tracky.navigation.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
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
                username = state.username,
                userEmail = state.email,
                events = mainViewModel.events,
                onLogout = mainViewModel::logout
            )
        }
    }
}

@Composable
private fun AppNavHost(
    isLoggedIn: Boolean,
    username: String?,
    userEmail: String?,
    events: Flow<MainEvent>,
    onLogout: () -> Unit
) {
    val startDestination = if (isLoggedIn) {
        Route.ProjectRoute.ProjectOverview
    } else {
        Route.AuthRoute.Login
    }

    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Route.AuthRoute.Login::class, Route.AuthRoute.Login.serializer())
                    subclass(Route.AuthRoute.Register::class, Route.AuthRoute.Register.serializer())
                    subclass(Route.AuthRoute.RegisterSuccess::class, Route.AuthRoute.RegisterSuccess.serializer())
                    subclass(Route.AuthRoute.EmailVerification::class, Route.AuthRoute.EmailVerification.serializer())
                    subclass(Route.AuthRoute.ForgotPassword::class, Route.AuthRoute.ForgotPassword.serializer())
                    subclass(Route.AuthRoute.ResetPassword::class, Route.AuthRoute.ResetPassword.serializer())
                    subclass(Route.ProjectRoute.ProjectOverview::class, Route.ProjectRoute.ProjectOverview.serializer())
                    subclass(Route.ProjectRoute.ProjectArchive::class, Route.ProjectRoute.ProjectArchive.serializer())
                    subclass(Route.ProjectRoute.ProjectArchiveDetail::class, Route.ProjectRoute.ProjectArchiveDetail.serializer())
                    subclass(Route.ProjectRoute.ProjectTrash::class, Route.ProjectRoute.ProjectTrash.serializer())
                    subclass(Route.ProjectRoute.ProjectDetail::class, Route.ProjectRoute.ProjectDetail.serializer())
                    subclass(Route.ProjectRoute.EditTextNavKey::class, Route.ProjectRoute.EditTextNavKey.serializer())
                    subclass(Route.ProjectRoute.TaskDetail::class, Route.ProjectRoute.TaskDetail.serializer())
                }
            }
        },
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
        backStack = backStack,
        username = username,
        userEmail = userEmail,
        onLogout = onLogout
    )
}
