package com.jvcs.tracky.features.auth.presentation.forgot_password

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.util.onFailure
import com.jvcs.tracky.core.domain.util.onSuccess
import com.jvcs.tracky.core.domain.validation.EmailValidator
import com.jvcs.tracky.design_system.util.asUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    private val authService: AuthService
) : ViewModel() {

    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(ForgotPasswordState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                observeTextStates()
                hasLoadedInitialData = true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ForgotPasswordState())

    private val isEmailValidFlow = snapshotFlow { _state.value.emailTextFieldState.text.toString() }
        .map { EmailValidator.validate(it) }
        .distinctUntilChanged()

    private val isLoadingFlow = _state.map { it.isLoading }.distinctUntilChanged()

    fun onAction(action: ForgotPasswordAction) {
        when (action) {
            ForgotPasswordAction.OnSubmitClick -> submit()
            ForgotPasswordAction.OnResendClick -> {
                _state.update { it.copy(isEmailSentSuccessfully = false) }
            }
            else -> Unit
        }
    }

    private fun observeTextStates() {
        combine(isEmailValidFlow, isLoadingFlow) { isEmailValid, isLoading ->
            _state.update { it.copy(canSubmit = !isLoading && isEmailValid) }
        }.launchIn(viewModelScope)
    }

    private fun submit() {
        if (!state.value.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorText = null) }
            authService.forgotPassword(
                email = state.value.emailTextFieldState.text.toString()
            ).onSuccess {
                _state.update { it.copy(isLoading = false, isEmailSentSuccessfully = true) }
            }.onFailure { error ->
                _state.update { it.copy(errorText = error.asUiText(), isLoading = false) }
            }
        }
    }
}
