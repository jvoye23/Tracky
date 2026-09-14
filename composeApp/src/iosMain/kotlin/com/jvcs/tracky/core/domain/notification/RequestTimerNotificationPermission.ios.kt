package com.jvcs.tracky.core.domain.notification

import androidx.compose.runtime.Composable

// Live Activities are governed by a Settings toggle, not a runtime prompt.
@Composable
actual fun RequestTimerNotificationPermission(request: Boolean) = Unit
