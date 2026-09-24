package com.jvcs.tracky.features.auth.domain

object EmailValidator {
    private const val EMAIL_PATTERN = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"

    fun validate(email: String): Boolean = EMAIL_PATTERN.toRegex().matches(email)
}
