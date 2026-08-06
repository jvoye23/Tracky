package com.jvcs.tracky

sealed interface MainEvent {
    data object OnSessionExpired: MainEvent
}