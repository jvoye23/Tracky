package com.jvcs.tracky.features.auth.presentation.login

sealed interface LoginAction {
    data object OnTogglePasswordVisibility : LoginAction
    data object OnForgotPasswordClick : LoginAction
    data object OnLoginClick : LoginAction
    data object OnSignUpClick : LoginAction
    data object OnGoogleSignInClick : LoginAction
    data object OnAppleSignInClick : LoginAction
}
