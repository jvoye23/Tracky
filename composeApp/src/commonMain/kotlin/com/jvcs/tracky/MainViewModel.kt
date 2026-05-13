package com.jvcs.tracky

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.auth.SessionStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainState(
    val isLoggedIn: Boolean = false,
    val isCheckingAuth: Boolean = true,
    val username: String? = null,
    val email: String? = null
)

class MainViewModel(
    private val sessionStorage: SessionStorage,
    private val authService: AuthService
) : ViewModel() {

    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(MainState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                observeAuthState()
                hasLoadedInitialData = true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), MainState())

    fun logout() {
        viewModelScope.launch {
            val refreshToken = sessionStorage.observeAuthInfo().firstOrNull()?.refreshToken
            if (!refreshToken.isNullOrBlank()) {
                authService.logout(refreshToken)
            }
            sessionStorage.set(null)
            authService.clearTokenCache()
        }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            sessionStorage.observeAuthInfo().collect { authInfo ->
                _state.update {
                    it.copy(
                        isLoggedIn = authInfo != null,
                        isCheckingAuth = false,
                        username = authInfo?.user?.username,
                        email = authInfo?.user?.email
                    )
                }
            }
        }
    }
}
