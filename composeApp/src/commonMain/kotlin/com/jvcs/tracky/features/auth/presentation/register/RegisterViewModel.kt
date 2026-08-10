package com.jvcs.tracky.features.auth.presentation.register

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
import com.jvcs.tracky.core.domain.validation.PasswordValidator
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
import tracky.composeapp.generated.resources.error_account_exists
import tracky.composeapp.generated.resources.error_invalid_email
import tracky.composeapp.generated.resources.error_invalid_name
import tracky.composeapp.generated.resources.error_invalid_password
import tracky.composeapp.generated.resources.passwords_do_not_match
import tracky.composeapp.generated.resources.terms_required

class RegisterViewModel(
    private val authService: AuthService,
    private val sessionStorage: SessionStorage,
    private val socialAuthProvider: SocialAuthProvider,
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

    private val isNameValidFlow = snapshotFlow { _state.value.nameTextState.text.toString() }
        .map { it.trim().length >= 2 }
        .distinctUntilChanged()

    private val isEmailValidFlow = snapshotFlow { _state.value.emailTextState.text.toString() }
        .map { EmailValidator.validate(it) }
        .distinctUntilChanged()

    private val isPasswordValidFlow = snapshotFlow { _state.value.passwordTextState.text.toString() }
        .map { PasswordValidator.validate(it).isValidPassword }
        .distinctUntilChanged()

    private val isConfirmPasswordValidFlow = combine(
        snapshotFlow { _state.value.passwordTextState.text.toString() },
        snapshotFlow { _state.value.confirmPasswordTextState.text.toString() },
    ) { pwd, confirm -> pwd.isNotEmpty() && pwd == confirm }
        .distinctUntilChanged()

    private val agreedFlow = _state.map { it.agreedToTerms }.distinctUntilChanged()
    private val isRegisteringFlow = _state.map { it.isRegistering }.distinctUntilChanged()

    fun onAction(action: RegisterAction) {
        when (action) {
            RegisterAction.OnRegisterClick -> register()
            RegisterAction.OnTogglePasswordVisibilityClick -> {
                _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            }
            RegisterAction.OnToggleConfirmPasswordVisibilityClick -> {
                _state.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
            }
            is RegisterAction.OnTermsToggle -> {
                _state.update { it.copy(agreedToTerms = action.agreed, termsError = null) }
            }
            RegisterAction.OnGoogleSignInClick -> signInWithGoogle()
            RegisterAction.OnAppleSignInClick -> signInWithApple()
            else -> Unit
        }
    }

    private data class FieldValidity(
        val name: Boolean,
        val email: Boolean,
        val password: Boolean,
        val confirmPassword: Boolean,
    )

    private fun observeTextStates() {
        combine(
            combine(
                isNameValidFlow,
                isEmailValidFlow,
                isPasswordValidFlow,
                isConfirmPasswordValidFlow,
            ) { name, email, pwd, confirm -> FieldValidity(name, email, pwd, confirm) },
            agreedFlow,
            isRegisteringFlow,
        ) { validity, agreed, isRegistering ->
            _state.update {
                it.copy(
                    isNameValid = validity.name,
                    isEmailValid = validity.email,
                    isPasswordValid = validity.password,
                    isConfirmPasswordValid = validity.confirmPassword,
                    canRegister = !isRegistering &&
                            validity.name &&
                            validity.email &&
                            validity.password &&
                            validity.confirmPassword &&
                            agreed,
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun validateFormInputs(): Boolean {
        val name = _state.value.nameTextState.text.toString()
        val email = _state.value.emailTextState.text.toString()
        val password = _state.value.passwordTextState.text.toString()
        val confirm = _state.value.confirmPasswordTextState.text.toString()
        val agreed = _state.value.agreedToTerms

        val nameValid = name.trim().length >= 2
        val emailValid = EmailValidator.validate(email)
        val passwordValid = PasswordValidator.validate(password).isValidPassword
        val confirmValid = password.isNotEmpty() && password == confirm

        _state.update {
            it.copy(
                nameError = if (!nameValid) UiText.Resource(Res.string.error_invalid_name) else null,
                emailError = if (!emailValid) UiText.Resource(Res.string.error_invalid_email) else null,
                passwordError = if (!passwordValid) UiText.Resource(Res.string.error_invalid_password) else null,
                confirmPasswordError = if (!confirmValid) UiText.Resource(Res.string.passwords_do_not_match) else null,
                termsError = if (!agreed) UiText.Resource(Res.string.terms_required) else null,
            )
        }

        return nameValid && emailValid && passwordValid && confirmValid && agreed
    }

    private fun register() {
        if (!validateFormInputs()) return
        viewModelScope.launch {
            _state.update { it.copy(isRegistering = true, registrationError = null) }
            val email = state.value.emailTextState.text.toString()
            authService.register(
                email = email,
                name = state.value.nameTextState.text.toString().trim(),
                password = state.value.passwordTextState.text.toString()
            ).onSuccess {
                _state.update { it.copy(isRegistering = false) }
                eventChannel.send(RegisterEvent.Success(email))
            }.onFailure { error ->
                val errorMessage = when (error) {
                    DataError.Remote.CONFLICT -> UiText.Resource(Res.string.error_account_exists)
                    else -> error.toUiText()
                }
                _state.update { it.copy(registrationError = errorMessage, isRegistering = false) }
            }
        }
    }

    private fun signInWithGoogle() {
        viewModelScope.launch {
            _state.update { it.copy(isRegistering = true, registrationError = null) }
            socialAuthProvider.signInWithGoogle()
                .onSuccess { idToken ->
                    authService.loginWithGoogle(idToken)
                        .onSuccess { authInfo ->
                            sessionStorage.set(authInfo)
                            _state.update { it.copy(isRegistering = false) }
                            eventChannel.send(RegisterEvent.Success(""))
                        }
                        .onFailure { error ->
                            _state.update { it.copy(registrationError = error.toUiText(), isRegistering = false) }
                        }
                }
                .onFailure { error ->
                    _state.update { it.copy(registrationError = error.toUiText(), isRegistering = false) }
                }
        }
    }

    private fun signInWithApple() {
        viewModelScope.launch {
            _state.update { it.copy(isRegistering = true, registrationError = null) }
            socialAuthProvider.signInWithApple()
                .onSuccess { idToken ->
                    authService.loginWithApple(idToken)
                        .onSuccess { authInfo ->
                            sessionStorage.set(authInfo)
                            _state.update { it.copy(isRegistering = false) }
                            eventChannel.send(RegisterEvent.Success(""))
                        }
                        .onFailure { error ->
                            _state.update { it.copy(registrationError = error.toUiText(), isRegistering = false) }
                        }
                }
                .onFailure { error ->
                    _state.update { it.copy(registrationError = error.toUiText(), isRegistering = false) }
                }
        }
    }
}
