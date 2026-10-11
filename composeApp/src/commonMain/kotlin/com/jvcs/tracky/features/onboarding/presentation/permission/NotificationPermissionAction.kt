package com.jvcs.tracky.features.onboarding.presentation.permission

sealed interface NotificationPermissionAction {

    data object OnAllowClick : NotificationPermissionAction

    data object OnSkipClick : NotificationPermissionAction
}
