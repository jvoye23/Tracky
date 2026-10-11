package com.jvcs.tracky

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatus
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatusStore
import com.jvcs.tracky.navigation.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val sessionStorage: SessionStorage,
    private val onboardingStatusStore: OnboardingStatusStore,
) : ViewModel() {

    private val eventChannel = Channel<MainEvent>()
    val events = eventChannel.receiveAsFlow()

    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(MainState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    observeSession()
                    observeAppAccess()
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = MainState(),
            )

    private var previousRefreshToken: String? = null

    init {
        viewModelScope.launch {
            val authInfo = sessionStorage.observeAuthInfo().firstOrNull()
            val onboardingStatus = onboardingStatusStore.observe().first()
            val isLoggedIn = authInfo != null
            _state.update {
                it.copy(
                    isCheckingAuth = false,
                    isLoggedIn = isLoggedIn,
                    username = authInfo?.user?.username,
                    email = authInfo?.user?.email,
                    startRoute = startRouteFor(isLoggedIn, onboardingStatus),
                    hasAppAccess = hasAppAccess(isLoggedIn, onboardingStatus),
                )
            }
        }
    }

    private fun observeAppAccess() {
        combine(
            sessionStorage.observeAuthInfo().map { it != null }.distinctUntilChanged(),
            onboardingStatusStore.observe(),
        ) { isLoggedIn, onboardingStatus ->
            _state.update {
                it.copy(isLoggedIn = isLoggedIn, hasAppAccess = hasAppAccess(isLoggedIn, onboardingStatus))
            }
        }.launchIn(viewModelScope)
    }

    /**
     * A session outranks everything: whoever holds one is past onboarding, however they got it. A
     * paid-for Pro without an account reopens on registration until the account exists.
     */
    private fun startRouteFor(isLoggedIn: Boolean, onboardingStatus: OnboardingStatus): Route =
        when {
            isLoggedIn -> Route.ProjectRoute.ProjectOverview
            onboardingStatus == OnboardingStatus.AWAITING_ACCOUNT -> Route.AuthRoute.Register
            onboardingStatus == OnboardingStatus.COMPLETED_FREE -> Route.ProjectRoute.ProjectOverview
            else -> Route.OnboardingRoute.Welcome
        }

    private fun hasAppAccess(isLoggedIn: Boolean, onboardingStatus: OnboardingStatus): Boolean =
        isLoggedIn || onboardingStatus == OnboardingStatus.COMPLETED_FREE

    private fun observeSession() {
        sessionStorage
            .observeAuthInfo()
            .onEach { authInfo ->
                val currentRefreshToken = authInfo?.refreshToken
                val isSessionExpired = previousRefreshToken != null && currentRefreshToken == null
                if (isSessionExpired) {
                    sessionStorage.set(null)
                    _state.update {
                        it.copy(
                            isLoggedIn = false,
                        )
                    }
                    eventChannel.send(MainEvent.OnSessionExpired)
                }
                previousRefreshToken = currentRefreshToken
            }.launchIn(viewModelScope)
    }
}
