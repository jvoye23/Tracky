package com.jvcs.tracky.features.auth.domain

data class PasswordValidationState(
    val hasMinLength: Boolean = false,
    val hasDigit: Boolean = false,
    val hasUppercase: Boolean = false,
    val hasLowercase: Boolean = false,
    val hasSpecialChar: Boolean = false
) {
    val isValidPassword: Boolean
        get() = hasMinLength && hasDigit && hasUppercase && hasLowercase && hasSpecialChar
}