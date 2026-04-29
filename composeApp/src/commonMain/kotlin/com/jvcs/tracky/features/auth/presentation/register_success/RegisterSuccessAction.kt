package com.jvcs.tracky.features.auth.presentation.register_success

sealed interface RegisterSuccessAction {
    data object OnLoginClick : RegisterSuccessAction
    data object OnResendVerificationEmailClick : RegisterSuccessAction
    data object OnOpenEmailAppClick : RegisterSuccessAction
}
