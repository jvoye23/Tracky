package com.jvcs.tracky.core.domain.validation

object PasswordValidator {
    private const val MIN_PASSWORD_LENGTH = 8
    private const val SPECIAL_CHARS = "!@#\$%^&*()_+-=[]{}|;':\",./<>?"

    fun validate(password: String): PasswordValidationState {
        return PasswordValidationState(
            hasMinLength = password.length >= MIN_PASSWORD_LENGTH,
            hasDigit = password.any { it.isDigit() },
            hasUppercase = password.any { it.isUpperCase() },
            hasLowercase = password.any { it.isLowerCase() },
            hasSpecialChar = password.any { it in SPECIAL_CHARS }
        )
    }
}
