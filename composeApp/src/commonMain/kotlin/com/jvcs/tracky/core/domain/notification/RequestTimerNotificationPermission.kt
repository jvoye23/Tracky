package com.jvcs.tracky.core.domain.notification

import androidx.compose.runtime.Composable

/**
 * Asks for whatever the platform needs before it will show the timer notification, once [request]
 * turns true - which is the first time a timer actually runs, not app start. A permission dialog
 * makes sense to a user who has just pressed play and none at all to one who has just opened the app.
 *
 * Android has to ask: minSdk is 33, so POST_NOTIFICATIONS is always a runtime grant. iOS Live
 * Activities need no prompt, and the JVM has no notification surface, so both are no-ops.
 */
@Composable
expect fun RequestTimerNotificationPermission(request: Boolean)
