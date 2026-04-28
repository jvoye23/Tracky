package com.jvcs.tracky.features.auth.presentation.email_verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.util.onFailure
import com.jvcs.tracky.core.domain.util.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EmailVerificationViewModel(
    private val authService: AuthService,
    private val token: String
) : ViewModel() {

    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(EmailVerificationState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                verifyEmail()
                hasLoadedInitialData = true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), EmailVerificationState())

    fun onAction(action: EmailVerificationAction) {
        // Navigation-only actions handled in Root composable
    }

    private fun verifyEmail() {
        viewModelScope.launch {
            _state.update { it.copy(isVerifying = true) }
            authService.verifyEmail(token)
                .onSuccess {
                    _state.update { it.copy(isVerifying = false, isVerified = true) }
                }
                .onFailure {
                    _state.update { it.copy(isVerifying = false, hasFailed = true) }
                }
        }
    }
}
