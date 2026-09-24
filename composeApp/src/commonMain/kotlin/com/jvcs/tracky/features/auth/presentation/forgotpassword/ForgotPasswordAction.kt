package com.jvcs.tracky.features.auth.presentation.forgotpassword

sealed interface ForgotPasswordAction {
    data object OnSubmitClick : ForgotPasswordAction

    data object OnBackClick : ForgotPasswordAction

    data object OnResendClick : ForgotPasswordAction

    data object OnBackToLoginClick : ForgotPasswordAction
}
