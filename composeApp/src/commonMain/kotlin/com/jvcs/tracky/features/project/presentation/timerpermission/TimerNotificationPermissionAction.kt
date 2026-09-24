package com.jvcs.tracky.features.project.presentation.timerpermission

sealed interface TimerNotificationPermissionAction {

    /** Acknowledges the explanation and carries on without the notification. */
    data object OnConfirm : TimerNotificationPermissionAction

    /** Leaves for the system settings page, the only place the refusal can be undone. */
    data object OnOpenAppSettings : TimerNotificationPermissionAction
}
