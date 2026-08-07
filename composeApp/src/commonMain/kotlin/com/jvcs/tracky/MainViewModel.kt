package com.jvcs.tracky

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.util.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class MainViewModel(
    private val sessionStorage: SessionStorage,
    private val authService: AuthService,
    private val applicationScope: CoroutineScope
) : ViewModel() {
    private val eventChannel = Channel<MainEvent>()
    val events = eventChannel.receiveAsFlow()

    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(MainState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                observeSession()
                hasLoadedInitialData = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MainState()
        )

    private var previousRefreshToken: String? = null

    init {
        viewModelScope.launch {
            val authInfo = sessionStorage.observeAuthInfo().firstOrNull()
            _state.update { it.copy(
                isCheckingAuth = false,
                isLoggedIn = authInfo != null,
                username = authInfo?.user?.username,
                email = authInfo?.user?.email
            ) }
        }
    }

    fun logout() {
        applicationScope.launch {
            val refreshToken = sessionStorage.observeAuthInfo().firstOrNull()?.refreshToken
            if (refreshToken.isNullOrBlank()) {
                clearLocalSession()
                return@launch
            }
            authService.logout(refreshToken).onSuccess {
                clearLocalSession()
            }
        }
    }
    private suspend fun clearLocalSession() {
        sessionStorage.set(null)
        authService.clearTokenCache()
    }

    private fun observeSession() {
        sessionStorage
            .observeAuthInfo()
            .onEach { authInfo ->
                val currentRefreshToken = authInfo?.refreshToken
                val isSessionExpired = previousRefreshToken != null && currentRefreshToken == null
                if (isSessionExpired) {
                    sessionStorage.set(null)
                    _state.update { it.copy(
                        isLoggedIn = false
                    ) }
                    eventChannel.send(MainEvent.OnSessionExpired)
                }
                previousRefreshToken = currentRefreshToken
            }
            .launchIn(viewModelScope)
    }
}
