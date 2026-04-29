package com.jvcs.tracky.features.auth.presentation.login

import androidx.compose.foundation.text.input.TextFieldState
import com.jvcs.tracky.design_system.util.UiText

data class LoginState(
    val emailTextFieldState: TextFieldState = TextFieldState(),
    val passwordTextFieldState: TextFieldState = TextFieldState(),
    val isPasswordVisible: Boolean = false,
    val canLogin: Boolean = false,
    val isLoggingIn: Boolean = false,
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    val error: UiText? = null,
)
