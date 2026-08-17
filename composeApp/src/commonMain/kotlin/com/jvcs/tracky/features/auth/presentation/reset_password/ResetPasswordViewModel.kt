package com.jvcs.tracky.features.auth.presentation.reset_password

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.onFailure
import com.jvcs.tracky.core.domain.util.onSuccess
import com.jvcs.tracky.features.auth.domain.PasswordValidator
import com.jvcs.tracky.features.project.presentation.util.toUiText
import com.jvcs.tracky.design_system.util.UiText
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
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.error_reset_password_token_invalid
import tracky.composeapp.generated.resources.error_same_password

class ResetPasswordViewModel(
    private val authService: AuthService,
    private val token: String
) : ViewModel() {

    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(ResetPasswordState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                observeTextStates()
                hasLoadedInitialData = true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ResetPasswordState())

    private val isPasswordValidFlow = snapshotFlow { _state.value.passwordTextState.text.toString() }
        .map { PasswordValidator.validate(it).isValidPassword }
        .distinctUntilChanged()

    private val isLoadingFlow = _state.map { it.isLoading }.distinctUntilChanged()

    fun onAction(action: ResetPasswordAction) {
        when (action) {
            ResetPasswordAction.OnSubmitClick -> submit()
            ResetPasswordAction.OnTogglePasswordVisibilityClick -> {
                _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            }
            else -> Unit
        }
    }

    private fun observeTextStates() {
        combine(isPasswordValidFlow, isLoadingFlow) { isPasswordValid, isLoading ->
            _state.update { it.copy(canSubmit = !isLoading && isPasswordValid) }
        }.launchIn(viewModelScope)
    }

    private fun submit() {
        if (!state.value.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorText = null) }
            authService.resetPassword(
                newPassword = state.value.passwordTextState.text.toString(),
                token = token
            ).onSuccess {
                _state.update { it.copy(isLoading = false, isResetSuccessful = true) }
            }.onFailure { error ->
                val errorMessage = when (error) {
                    DataError.Remote.UNAUTHORIZED -> UiText.Resource(Res.string.error_reset_password_token_invalid)
                    DataError.Remote.CONFLICT -> UiText.Resource(Res.string.error_same_password)
                    else -> error.toUiText()
                }
                _state.update { it.copy(errorText = errorMessage, isLoading = false) }
            }
        }
    }
}
