package com.jvcs.tracky.features.auth.presentation.register

sealed interface RegisterAction {
    data object OnLoginClick : RegisterAction
    data object OnRegisterClick : RegisterAction
    data object OnTogglePasswordVisibilityClick : RegisterAction
    data object OnToggleConfirmPasswordVisibilityClick : RegisterAction
    data class OnTermsToggle(val agreed: Boolean) : RegisterAction
    data object OnGoogleSignInClick : RegisterAction
    data object OnAppleSignInClick : RegisterAction
}
