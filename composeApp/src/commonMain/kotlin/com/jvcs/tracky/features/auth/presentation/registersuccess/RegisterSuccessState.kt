package com.jvcs.tracky.features.auth.presentation.registersuccess

import com.jvcs.tracky.designsystem.util.UiText

data class RegisterSuccessState(
    val registeredEmail: String = "",
    val isResendingVerificationEmail: Boolean = false,
    val resendVerificationError: UiText? = null,
)
