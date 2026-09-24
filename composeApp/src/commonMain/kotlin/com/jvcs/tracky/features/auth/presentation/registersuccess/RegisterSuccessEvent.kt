package com.jvcs.tracky.features.auth.presentation.registersuccess

sealed interface RegisterSuccessEvent {
    data object ResendVerificationEmailSuccess : RegisterSuccessEvent
}
