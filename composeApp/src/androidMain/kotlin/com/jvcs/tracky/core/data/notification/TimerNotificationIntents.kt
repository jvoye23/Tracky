package com.jvcs.tracky.core.data.notification

/**
 * The contract between the notification, which lives here, and the activity that receives its tap,
 * which lives in the app module. An extra rather than a URI: this is an internal PendingIntent, not
 * a link anything outside the app can form, so there is no intent filter to match against.
 */
object TimerNotificationIntents {
    const val EXTRA_PROJECT_ID = "com.jvcs.tracky.extra.PROJECT_ID"
}
