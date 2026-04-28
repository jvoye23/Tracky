package com.jvcs.tracky.features.auth.presentation.forgot_password

sealed interface ForgotPasswordAction {
    data object OnSubmitClick : ForgotPasswordAction
    data object OnBackClick : ForgotPasswordAction
}
