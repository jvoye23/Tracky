package com.jvcs.tracky.core.domain.notification

/**
 * The platform surface that shows a running timer while the app is not on screen: an Android
 * foreground-service notification, an iOS Live Activity, nothing on JVM.
 *
 * Deliberately a plain interface rather than an `expect class`, matching `SyncScheduler` - the
 * implementations share no constructor shape, and only the platform Koin module names them.
 */
interface TimerNotificationController {

    /** Shows the notification, or updates the one already up. */
    suspend fun show(session: TimerNotificationSession)

    /** Takes it down. Safe to call when nothing is showing. */
    suspend fun dismiss()
}

/** For JVM, and for platforms before their surface is wired up. */
class NoOpTimerNotificationController : TimerNotificationController {
    override suspend fun show(session: TimerNotificationSession) = Unit

    override suspend fun dismiss() = Unit
}
