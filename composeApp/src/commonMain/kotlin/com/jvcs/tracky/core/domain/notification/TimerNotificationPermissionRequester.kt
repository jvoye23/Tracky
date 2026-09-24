package com.jvcs.tracky.core.domain.notification

/** What the platform said when asked to let the timer notification show. */
enum class TimerNotificationPermission {
    /** Granted now, or already held. */
    Granted,

    /** Refused this time. Asking again later is allowed. */
    Denied,

    /** Refused for good - the only route left is the app's settings page. */
    DeniedAlways,

    /** The platform has nothing to ask for. */
    NotRequired,
}

/**
 * Asks for whatever the platform needs before it will show the timer notification.
 *
 * Called the first time a timer actually runs, not at app start: a permission dialog makes sense to
 * a user who has just pressed play, and none at all to one who has just opened the app.
 *
 * Android has to ask, since minSdk is 33 and POST_NOTIFICATIONS is always a runtime grant. iOS Live
 * Activities are governed by a Settings toggle rather than a runtime prompt, and the JVM has no
 * notification surface, so both answer [TimerNotificationPermission.NotRequired].
 *
 * Deliberately a plain interface rather than an `expect fun`, matching [TimerNotificationController]
 * - the implementations share no constructor shape, and only the platform Koin module names them.
 * Keeping it out of Compose is the point: a `@Composable expect fun` put the UI framework in domain
 * and left the answer nowhere to go.
 */
interface TimerNotificationPermissionRequester {

    /** Asks once, returning what the platform answered. Never throws. */
    suspend fun request(): TimerNotificationPermission

    /** Opens the app's system settings page, the only route left after a permanent refusal. */
    fun openAppSettings()
}

/** For JVM and iOS, and for platforms before their surface is wired up. */
class NoOpTimerNotificationPermissionRequester : TimerNotificationPermissionRequester {
    override suspend fun request() = TimerNotificationPermission.NotRequired

    override fun openAppSettings() = Unit
}
