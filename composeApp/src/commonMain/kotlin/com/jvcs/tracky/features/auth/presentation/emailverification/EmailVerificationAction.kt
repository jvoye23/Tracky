package com.jvcs.tracky.features.auth.presentation.emailverification

sealed interface EmailVerificationAction {
    data object OnLoginClick : EmailVerificationAction

    data object OnCloseClick : EmailVerificationAction
}
