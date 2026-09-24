package com.jvcs.tracky.features.project.presentation.timerpermission

data class TimerNotificationPermissionState(
    /** Set when the platform refused, so the user learns what they have given up. */
    val showDeniedDialog: Boolean = false,
)
