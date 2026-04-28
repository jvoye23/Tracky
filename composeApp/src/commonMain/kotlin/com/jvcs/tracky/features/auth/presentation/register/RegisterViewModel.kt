package com.jvcs.tracky.features.auth.presentation.register

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.onFailure
import com.jvcs.tracky.core.domain.util.onSuccess
import com.jvcs.tracky.core.domain.validation.EmailValidator
import com.jvcs.tracky.core.domain.validation.PasswordValidator
import com.jvcs.tracky.design_system.util.UiText
import com.jvcs.tracky.design_system.util.asUiText
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
import tracky.composeapp.generated.resources.error_account_exists
import tracky.composeapp.generated.resources.error_invalid_email
import tracky.composeapp.generated.resources.error_invalid_password
import tracky.composeapp.generated.resources.error_invalid_username

class RegisterViewModel(
    private val authService: AuthService
) : ViewModel() {

    private var hasLoadedInitialData = false

    private val eventChannel = Channel<RegisterEvent>()
    val events = eventChannel.receiveAsFlow()

    private val _state = MutableStateFlow(RegisterState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                observeTextStates()
                hasLoadedInitialData = true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), RegisterState())

    private val isEmailValidFlow = snapshotFlow { _state.value.emailTextState.text.toString() }
        .map { EmailValidator.validate(it) }
        .distinctUntilChanged()

    private val isUsernameValidFlow = snapshotFlow { _state.value.usernameTextState.text.toString() }
        .map { it.length in 3..20 }
        .distinctUntilChanged()

    private val isPasswordValidFlow = snapshotFlow { _state.value.passwordTextState.text.toString() }
        .map { PasswordValidator.validate(it).isValidPassword }
        .distinctUntilChanged()

    private val isRegisteringFlow = _state.map { it.isRegistering }.distinctUntilChanged()

    fun onAction(action: RegisterAction) {
        when (action) {
            RegisterAction.OnRegisterClick -> register()
            RegisterAction.OnTogglePasswordVisibilityClick -> {
                _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            }
            else -> Unit
        }
    }

    private fun observeTextStates() {
        combine(
            isEmailValidFlow,
            isUsernameValidFlow,
            isPasswordValidFlow,
            isRegisteringFlow
        ) { isEmailValid, isUsernameValid, isPasswordValid, isRegistering ->
            _state.update {
                it.copy(
                    isEmailValid = isEmailValid,
                    isUsernameValid = isUsernameValid,
                    isPasswordValid = isPasswordValid,
                    canRegister = !isRegistering && isEmailValid && isUsernameValid && isPasswordValid
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun validateFormInputs(): Boolean {
        val emailValid = EmailValidator.validate(_state.value.emailTextState.text.toString())
        val usernameValid = _state.value.usernameTextState.text.toString().length in 3..20
        val passwordValid = PasswordValidator.validate(_state.value.passwordTextState.text.toString()).isValidPassword

        _state.update {
            it.copy(
                emailError = if (!emailValid) UiText.Resource(Res.string.error_invalid_email) else null,
                usernameError = if (!usernameValid) UiText.Resource(Res.string.error_invalid_username) else null,
                passwordError = if (!passwordValid) UiText.Resource(Res.string.error_invalid_password) else null
            )
        }

        return emailValid && usernameValid && passwordValid
    }

    private fun register() {
        if (!validateFormInputs()) return
        viewModelScope.launch {
            _state.update { it.copy(isRegistering = true, registrationError = null) }
            val email = state.value.emailTextState.text.toString()
            authService.register(
                email = email,
                username = state.value.usernameTextState.text.toString(),
                password = state.value.passwordTextState.text.toString()
            ).onSuccess {
                _state.update { it.copy(isRegistering = false) }
                eventChannel.send(RegisterEvent.Success(email))
            }.onFailure { error ->
                val errorMessage = when (error) {
                    DataError.Network.CONFLICT -> UiText.Resource(Res.string.error_account_exists)
                    else -> error.asUiText()
                }
                _state.update { it.copy(registrationError = errorMessage, isRegistering = false) }
            }
        }
    }
}
