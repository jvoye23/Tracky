package com.jvcs.tracky.features.auth.presentation.login

sealed interface LoginEvent {
    data object Success : LoginEvent
}
