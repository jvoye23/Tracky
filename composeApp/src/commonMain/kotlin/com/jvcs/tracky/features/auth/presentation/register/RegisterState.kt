package com.jvcs.tracky.features.auth.presentation.register

import androidx.compose.foundation.text.input.TextFieldState
import com.jvcs.tracky.design_system.util.UiText

data class RegisterState(
    val nameTextState: TextFieldState = TextFieldState(),
    val isNameValid: Boolean = false,
    val nameError: UiText? = null,
    val emailTextState: TextFieldState = TextFieldState(),
    val isEmailValid: Boolean = false,
    val emailError: UiText? = null,
    val passwordTextState: TextFieldState = TextFieldState(),
    val isPasswordValid: Boolean = false,
    val passwordError: UiText? = null,
    val confirmPasswordTextState: TextFieldState = TextFieldState(),
    val isConfirmPasswordValid: Boolean = false,
    val confirmPasswordError: UiText? = null,
    val agreedToTerms: Boolean = false,
    val termsError: UiText? = null,
    val registrationError: UiText? = null,
    val isRegistering: Boolean = false,
    val canRegister: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
)
