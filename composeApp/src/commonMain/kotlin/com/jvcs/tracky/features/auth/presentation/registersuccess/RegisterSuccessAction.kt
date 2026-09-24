package com.jvcs.tracky.features.auth.presentation.registersuccess

sealed interface RegisterSuccessAction {

    data object OnLoginClick : RegisterSuccessAction

    data object OnResendVerificationEmailClick : RegisterSuccessAction

    data object OnOpenEmailAppClick : RegisterSuccessAction
}
