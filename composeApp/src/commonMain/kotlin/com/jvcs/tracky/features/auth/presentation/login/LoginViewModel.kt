package com.jvcs.tracky.features.auth.presentation.login

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.auth.SocialAuthProvider
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.onFailure
import com.jvcs.tracky.core.domain.util.onSuccess
import com.jvcs.tracky.core.domain.validation.EmailValidator
import com.jvcs.tracky.core.presentation.util.toUiText
import com.jvcs.tracky.design_system.util.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.error_email_not_verified
import tracky.composeapp.generated.resources.error_invalid_credentials

class LoginViewModel(
    private val authService: AuthService,
    private val sessionStorage: SessionStorage,
    private val socialAuthProvider: SocialAuthProvider,
) : ViewModel() {

    private var hasLoadedInitialData = false

    private val eventChannel = Channel<LoginEvent>()
    val events = eventChannel.receiveAsFlow()

    private val _state = MutableStateFlow(LoginState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                observeTextStates()
                hasLoadedInitialData = true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LoginState())

    private val isEmailValidFlow = snapshotFlow { _state.value.emailTextFieldState.text.toString() }
        .map { email -> EmailValidator.validate(email) }
        .distinctUntilChanged()

    private val isPasswordNotBlankFlow = snapshotFlow { _state.value.passwordTextFieldState.text.toString() }
        .map { it.isNotBlank() }
        .distinctUntilChanged()

    private val isLoggingInFlow = _state.map { it.isLoggingIn }.distinctUntilChanged()

    fun onAction(action: LoginAction) {
        when (action) {
            LoginAction.OnLoginClick -> login()
            LoginAction.OnTogglePasswordVisibility -> {
                _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            }
            LoginAction.OnGoogleSignInClick -> signInWithGoogle()
            LoginAction.OnAppleSignInClick -> signInWithApple()
            else -> Unit
        }
    }

    private fun observeTextStates() {
        combine(isEmailValidFlow, isPasswordNotBlankFlow, isLoggingInFlow) { isEmailValid, isPasswordNotBlank, isLoggingIn ->
            _state.update { it.copy(canLogin = !isLoggingIn && isEmailValid && isPasswordNotBlank) }
        }.launchIn(viewModelScope)
    }

    private fun login() {
        if (!state.value.canLogin) return
        viewModelScope.launch {
            _state.update { it.copy(isLoggingIn = true, error = null) }
            authService.login(
                email = state.value.emailTextFieldState.text.toString(),
                password = state.value.passwordTextFieldState.text.toString()
            ).onSuccess { authInfo ->
                sessionStorage.set(authInfo)
                _state.update { it.copy(isLoggingIn = false) }
                eventChannel.send(LoginEvent.Success)
            }.onFailure { error ->
                val errorMessage = when (error) {
                    DataError.Remote.UNAUTHORIZED -> UiText.Resource(Res.string.error_invalid_credentials)
                    DataError.Remote.FORBIDDEN -> UiText.Resource(Res.string.error_email_not_verified)
                    else -> error.toUiText()
                }
                _state.update { it.copy(error = errorMessage, isLoggingIn = false) }
            }
        }
    }

    private fun signInWithGoogle() {
        viewModelScope.launch {
            _state.update { it.copy(isLoggingIn = true, error = null) }
            socialAuthProvider.signInWithGoogle()
                .onSuccess { idToken ->
                    authService.loginWithGoogle(idToken)
                        .onSuccess { authInfo ->
                            sessionStorage.set(authInfo)
                            _state.update { it.copy(isLoggingIn = false) }
                            eventChannel.send(LoginEvent.Success)
                        }
                        .onFailure { error ->
                            _state.update { it.copy(error = error.toUiText(), isLoggingIn = false) }
                        }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.toUiText(), isLoggingIn = false) }
                }
        }
    }

    private fun signInWithApple() {
        viewModelScope.launch {
            _state.update { it.copy(isLoggingIn = true, error = null) }
            socialAuthProvider.signInWithApple()
                .onSuccess { idToken ->
                    authService.loginWithApple(idToken)
                        .onSuccess { authInfo ->
                            sessionStorage.set(authInfo)
                            _state.update { it.copy(isLoggingIn = false) }
                            eventChannel.send(LoginEvent.Success)
                        }
                        .onFailure { error ->
                            _state.update { it.copy(error = error.toUiText(), isLoggingIn = false) }
                        }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.toUiText(), isLoggingIn = false) }
                }
        }
    }
}
