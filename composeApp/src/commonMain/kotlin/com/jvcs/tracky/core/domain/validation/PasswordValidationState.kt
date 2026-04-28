package com.jvcs.tracky.core.domain.validation

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
