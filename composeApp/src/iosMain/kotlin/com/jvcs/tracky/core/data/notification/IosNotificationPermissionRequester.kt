package com.jvcs.tracky.core.data.notification

import com.jvcs.tracky.core.domain.notification.TimerNotificationPermission
import com.jvcs.tracky.core.domain.notification.TimerNotificationPermissionRequester
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

/**
 * The permission console pushes need. Live Activities are governed by a Settings toggle instead, so
 * the timer does not depend on the answer: a refusal comes back as
 * [TimerNotificationPermission.NotRequired], which keeps the "notifications are off" dialog away
 * from a timer that is still showing.
 */
class IosNotificationPermissionRequester : TimerNotificationPermissionRequester {

    private val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()

    /**
     * Cached because the status only comes back through a callback and [needsPrompt] has to answer
     * straight away. Starts true: until the status arrives, an extra onboarding step is the safe
     * mistake, since skipping it may lose the only ask a new user gets.
     */
    @Volatile
    private var isUndetermined = true

    init {
        notificationCenter.getNotificationSettingsWithCompletionHandler { settings ->
            isUndetermined = settings?.authorizationStatus == UNAuthorizationStatusNotDetermined
        }
    }

    override fun needsPrompt(): Boolean = isUndetermined

    override suspend fun request(): TimerNotificationPermission =
        suspendCancellableCoroutine { continuation ->
            notificationCenter.requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
            ) { isGranted, _ ->
                isUndetermined = false
                continuation.resume(
                    if (isGranted) TimerNotificationPermission.Granted else TimerNotificationPermission.NotRequired,
                )
            }
        }

    override fun openAppSettings() {
        val settingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(settingsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
