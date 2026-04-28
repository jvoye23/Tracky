package com.jvcs.tracky.features.auth.presentation.email_verification

data class EmailVerificationState(
    val isVerifying: Boolean = false,
    val isVerified: Boolean = false,
    val hasFailed: Boolean = false
)
