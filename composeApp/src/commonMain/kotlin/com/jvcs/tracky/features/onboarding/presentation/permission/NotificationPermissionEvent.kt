package com.jvcs.tracky.features.onboarding.presentation.permission

sealed interface NotificationPermissionEvent {

    /** The step is over, whatever the answer was. */
    data object Finished : NotificationPermissionEvent
}
